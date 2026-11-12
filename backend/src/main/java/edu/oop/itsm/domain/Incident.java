package edu.oop.itsm.domain;

import edu.oop.itsm.exception.InvalidStateTransitionException;
import edu.oop.itsm.exception.ValidationException;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root of the domain. All state changes go through intention-revealing methods
 * (assignTo, acknowledge, resolve, ...) which delegate legality checks to {@link IncidentStatus}.
 * No public setters for lifecycle fields: the only way to change state is a valid transition.
 */
@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "ix_inc_status", columnList = "status"),
        @Index(name = "ix_inc_priority", columnList = "priority"),
        @Index(name = "ix_inc_created", columnList = "createdAt"),
        @Index(name = "ix_inc_sla", columnList = "slaStatus"),
        @Index(name = "ix_inc_service", columnList = "service_id"),
        @Index(name = "ix_inc_assignee", columnList = "assignee_id"),
        @Index(name = "ix_inc_reporter", columnList = "reporter_id")})
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ItService service;

    @ManyToOne(fetch = FetchType.LAZY)
    private Technician assignee;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private Impact impact;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private Urgency urgency;

    private int affectedUsers;

    @Enumerated(EnumType.STRING) @Column(length = 4)
    private Priority priority;

    private double priorityScore;

    @Column(length = 500)
    private String priorityReason;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.NEW;

    private boolean autoCategorized;
    private Double categoryConfidence;

    // ---- SLA ----
    @Column(length = 60)
    private String slaPolicyName;
    private int responseTargetMinutes;
    private int resolutionTargetMinutes;
    private Instant responseDueAt;
    private Instant resolutionDueAt;
    private long pausedSeconds;
    private Instant pausedAt;
    @Enumerated(EnumType.STRING) @Column(length = 10)
    private SlaStatus slaStatus = SlaStatus.ON_TRACK;
    private boolean responseBreached;
    private boolean resolutionBreached;
    @Enumerated(EnumType.STRING) @Column(length = 15)
    private EscalationLevel escalationLevel = EscalationLevel.NONE;

    // ---- intelligence ----
    @ManyToOne(fetch = FetchType.LAZY)
    private Incident duplicateOf;
    private Double duplicateScore;

    // ---- resolution / closure ----
    @Column(length = 4000)
    private String resolution;
    @Column(length = 1000)
    private String rootCause;
    @Column(length = 1000)
    private String closureNotes;
    private Integer feedbackRating;
    @Column(length = 1000)
    private String feedbackComment;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    private Instant updatedAt;
    private Instant acknowledgedAt;
    private Instant resolvedAt;
    private Instant closedAt;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<IncidentComment> comments = new ArrayList<>();

    protected Incident() {}

    public Incident(String title, String description, User reporter, ItService service,
                    Impact impact, Urgency urgency, int affectedUsers, Instant now) {
        if (title == null || title.isBlank()) throw new ValidationException("Title is required");
        if (description == null || description.isBlank()) throw new ValidationException("Description is required");
        if (affectedUsers < 1) throw new ValidationException("Affected users must be at least 1");
        this.title = title.trim();
        this.description = description.trim();
        this.reporter = Objects.requireNonNull(reporter);
        this.service = Objects.requireNonNull(service);
        this.impact = Objects.requireNonNull(impact);
        this.urgency = Objects.requireNonNull(urgency);
        this.affectedUsers = affectedUsers;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getNumber() { return id == null ? "INC-NEW" : "INC" + (1000 + id); }

    // ================= lifecycle =================

    private void moveTo(IncidentStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Incident %s cannot move from %s to %s".formatted(getNumber(), status, target));
        }
        status = target;
        updatedAt = now;
    }

    /** Sets the category; the first classification also advances NEW -> CLASSIFIED. */
    public void classify(Category category, boolean automatic, Double confidence, Instant now) {
        this.category = Objects.requireNonNull(category);
        this.autoCategorized = automatic;
        this.categoryConfidence = confidence;
        if (status == IncidentStatus.NEW) moveTo(IncidentStatus.CLASSIFIED, now);
        else updatedAt = now;
    }

    public void applyPriority(Priority priority, double score, String reason) {
        this.priority = Objects.requireNonNull(priority);
        this.priorityScore = score;
        this.priorityReason = reason;
    }

    /** (Re)computes deadlines from creation time + targets + any time spent paused awaiting the user. */
    public void applySla(SlaPolicy policy) {
        if (priority == null) throw new IllegalStateException("Priority must be calculated before SLA");
        SlaTarget t = policy.targetFor(priority);
        slaPolicyName = policy.getName();
        responseTargetMinutes = t.getResponseMinutes();
        resolutionTargetMinutes = t.getResolutionMinutes();
        responseDueAt = createdAt.plus(t.response());
        resolutionDueAt = createdAt.plus(t.resolution()).plusSeconds(pausedSeconds);
    }

    public void assignTo(Technician technician, Instant now) {
        Objects.requireNonNull(technician);
        moveTo(IncidentStatus.ASSIGNED, now);
        this.assignee = technician;
        this.acknowledgedAt = null;
    }

    public void acknowledge(Instant now) {
        moveTo(IncidentStatus.ACKNOWLEDGED, now);
        acknowledgedAt = now;
        if (responseDueAt != null && now.isAfter(responseDueAt)) responseBreached = true;
    }

    public void startWork(Instant now) { moveTo(IncidentStatus.IN_PROGRESS, now); }

    /** Pauses the resolution SLA clock until the reporter answers. */
    public void requestInfo(Instant now) {
        moveTo(IncidentStatus.AWAITING_INFO, now);
        pausedAt = now;
    }

    public void provideInfo(Instant now) {
        moveTo(IncidentStatus.IN_PROGRESS, now);
        if (pausedAt != null) {
            long paused = Duration.between(pausedAt, now).getSeconds();
            pausedSeconds += paused;
            if (resolutionDueAt != null) resolutionDueAt = resolutionDueAt.plusSeconds(paused);
            pausedAt = null;
        }
    }

    public void resolve(String resolution, String rootCause, Instant now) {
        if (resolution == null || resolution.isBlank()) throw new ValidationException("Resolution details are required");
        moveTo(IncidentStatus.RESOLVED, now);
        this.resolution = resolution.trim();
        this.rootCause = rootCause == null || rootCause.isBlank() ? null : rootCause.trim();
        this.resolvedAt = now;
        if (resolutionDueAt != null && now.isAfter(resolutionDueAt)) resolutionBreached = true;
        slaStatus = isBreached() ? SlaStatus.BREACHED : SlaStatus.MET;
    }

    public void verify(Instant now) { moveTo(IncidentStatus.VERIFIED, now); }

    public void reopen(Instant now) {
        moveTo(IncidentStatus.IN_PROGRESS, now);
        resolvedAt = null;
        slaStatus = isBreached() ? SlaStatus.BREACHED : SlaStatus.ON_TRACK;
    }

    public void close(String notes, Instant now) {
        moveTo(IncidentStatus.CLOSED, now);
        closedAt = now;
        closureNotes = notes;
    }

    public void cancel(String reason, Instant now) {
        if (reason == null || reason.isBlank()) throw new ValidationException("A cancellation reason is required");
        moveTo(IncidentStatus.CANCELLED, now);
        closedAt = now;
        closureNotes = reason.trim();
    }

    // ================= other behaviour =================

    public IncidentComment addComment(User author, CommentType type, String body, Instant now) {
        if (body == null || body.isBlank()) throw new ValidationException("Comment text is required");
        if (body.length() > 4000) throw new ValidationException("Comment is too long (max 4000 characters)");
        IncidentComment c = new IncidentComment(this, author, type, body.trim(), now);
        comments.add(c);
        updatedAt = now;
        return c;
    }

    public void updateDetails(String newTitle, String newDescription, Instant now) {
        if (!status.isOpen()) throw new InvalidStateTransitionException("Closed or resolved incidents cannot be edited");
        if (newTitle != null && !newTitle.isBlank()) title = newTitle.trim();
        if (newDescription != null && !newDescription.isBlank()) description = newDescription.trim();
        updatedAt = now;
    }

    public void reassess(Impact impact, Urgency urgency, Integer affectedUsers, ItService service) {
        if (!status.isOpen()) throw new InvalidStateTransitionException("Cannot reassess an incident that is no longer open");
        if (impact != null) this.impact = impact;
        if (urgency != null) this.urgency = urgency;
        if (affectedUsers != null) {
            if (affectedUsers < 1) throw new ValidationException("Affected users must be at least 1");
            this.affectedUsers = affectedUsers;
        }
        if (service != null) this.service = service;
    }

    public void markPossibleDuplicateOf(Incident original, double score) {
        if (original == this) return;
        this.duplicateOf = original;
        this.duplicateScore = score;
    }

    public void recordFeedback(int rating, String comment) {
        if (status != IncidentStatus.RESOLVED && status != IncidentStatus.VERIFIED && status != IncidentStatus.CLOSED)
            throw new InvalidStateTransitionException("Feedback can only be given after resolution");
        if (rating < 1 || rating > 5) throw new ValidationException("Rating must be between 1 and 5");
        this.feedbackRating = rating;
        this.feedbackComment = comment;
    }

    public EscalationLevel escalate(Instant now) {
        escalationLevel = escalationLevel.next();
        updatedAt = now;
        return escalationLevel;
    }

    /**
     * Re-evaluates SLA health. Returns the new status so callers can react to changes.
     * AT_RISK once {@code warnThreshold} (e.g. 0.75) of the window has elapsed, BREACHED after the deadline.
     */
    public SlaStatus evaluateSla(Instant now, double warnThreshold) {
        if (!status.isOpen() || resolutionDueAt == null) return slaStatus;
        if (!status.slaClockRunning()) return slaStatus = isBreached() ? SlaStatus.BREACHED : SlaStatus.PAUSED;
        if (acknowledgedAt == null && now.isAfter(responseDueAt)) responseBreached = true;
        if (now.isAfter(resolutionDueAt)) resolutionBreached = true;
        if (isBreached()) return slaStatus = SlaStatus.BREACHED;
        double fraction = Math.max(acknowledgedAt == null ? responseElapsedFraction(now) : 0, resolutionElapsedFraction(now));
        return slaStatus = fraction >= warnThreshold ? SlaStatus.AT_RISK : SlaStatus.ON_TRACK;
    }

    public double responseElapsedFraction(Instant now) {
        if (responseDueAt == null) return 0;
        return fraction(Duration.between(createdAt, now), Duration.between(createdAt, responseDueAt));
    }

    public double resolutionElapsedFraction(Instant now) {
        if (resolutionDueAt == null) return 0;
        Duration paused = Duration.ofSeconds(pausedSeconds);
        return fraction(Duration.between(createdAt, now).minus(paused), Duration.between(createdAt, resolutionDueAt).minus(paused));
    }

    private static double fraction(Duration elapsed, Duration window) {
        if (window.isZero() || window.isNegative()) return 1;
        return Math.max(0, (double) elapsed.toSeconds() / window.toSeconds());
    }

    public boolean isBreached() { return responseBreached || resolutionBreached; }
    public boolean isOpen() { return status.isOpen(); }

    // ================= getters =================

    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public User getReporter() { return reporter; }
    public Category getCategory() { return category; }
    public ItService getService() { return service; }
    public Technician getAssignee() { return assignee; }
    public Impact getImpact() { return impact; }
    public Urgency getUrgency() { return urgency; }
    public int getAffectedUsers() { return affectedUsers; }
    public Priority getPriority() { return priority; }
    public double getPriorityScore() { return priorityScore; }
    public String getPriorityReason() { return priorityReason; }
    public IncidentStatus getStatus() { return status; }
    public boolean isAutoCategorized() { return autoCategorized; }
    public Double getCategoryConfidence() { return categoryConfidence; }
    public String getSlaPolicyName() { return slaPolicyName; }
    public int getResponseTargetMinutes() { return responseTargetMinutes; }
    public int getResolutionTargetMinutes() { return resolutionTargetMinutes; }
    public Instant getResponseDueAt() { return responseDueAt; }
    public Instant getResolutionDueAt() { return resolutionDueAt; }
    public SlaStatus getSlaStatus() { return slaStatus; }
    public boolean isResponseBreached() { return responseBreached; }
    public boolean isResolutionBreached() { return resolutionBreached; }
    public EscalationLevel getEscalationLevel() { return escalationLevel; }
    public Incident getDuplicateOf() { return duplicateOf; }
    public Double getDuplicateScore() { return duplicateScore; }
    public String getResolution() { return resolution; }
    public String getRootCause() { return rootCause; }
    public String getClosureNotes() { return closureNotes; }
    public Integer getFeedbackRating() { return feedbackRating; }
    public String getFeedbackComment() { return feedbackComment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
    public List<IncidentComment> getComments() { return Collections.unmodifiableList(comments); }
}
