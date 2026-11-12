package edu.oop.itsm.service;

import edu.oop.itsm.assignment.AssignmentService;
import edu.oop.itsm.domain.*;
import edu.oop.itsm.event.Actor;
import edu.oop.itsm.event.IncidentEvent;
import edu.oop.itsm.exception.*;
import edu.oop.itsm.intelligence.CategoryClassifier;
import edu.oop.itsm.intelligence.CorrelationEngine;
import edu.oop.itsm.intelligence.PriorityEngine;
import edu.oop.itsm.intelligence.SimilarityService;
import edu.oop.itsm.repository.*;
import edu.oop.itsm.web.dto.IncidentDtos.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Application service orchestrating the incident lifecycle:
 * Report -> Classify -> Prioritise -> SLA -> Duplicate check -> Assign -> ... -> Close.
 * Business rules live in the domain (Incident, IncidentStatus, IncidentAction); this class coordinates
 * collaborators and publishes events that the audit, notification and live-feed observers react to.
 */
@Service
@Transactional
public class IncidentService {

    private final IncidentRepository incidents;
    private final ItServiceRepository services;
    private final CategoryRepository categories;
    private final SlaPolicyRepository slaPolicies;
    private final TechnicianRepository technicians;
    private final UserRepository users;
    private final AuditRepository audit;
    private final AssignmentService assignment;
    private final PriorityEngine priorityEngine;
    private final CategoryClassifier classifier;
    private final SimilarityService similarity;
    private final CorrelationEngine correlation;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public IncidentService(IncidentRepository incidents, ItServiceRepository services, CategoryRepository categories,
                           SlaPolicyRepository slaPolicies, TechnicianRepository technicians, UserRepository users,
                           AuditRepository audit, AssignmentService assignment, PriorityEngine priorityEngine,
                           CategoryClassifier classifier, SimilarityService similarity, CorrelationEngine correlation,
                           SettingsService settings, ApplicationEventPublisher events, Clock clock) {
        this.incidents = incidents;
        this.services = services;
        this.categories = categories;
        this.slaPolicies = slaPolicies;
        this.technicians = technicians;
        this.users = users;
        this.audit = audit;
        this.assignment = assignment;
        this.priorityEngine = priorityEngine;
        this.classifier = classifier;
        this.similarity = similarity;
        this.correlation = correlation;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
    }

    // =============================== create pipeline ===============================

    public Detail create(CreateIncident req, Actor actor) {
        Instant now = clock.instant();
        ItService service = services.findWithPolicyById(req.serviceId())
                .orElseThrow(() -> new NotFoundException("Service " + req.serviceId() + " not found"));
        if (!service.isActive()) throw new ValidationException("Service '" + service.getName() + "' is retired");

        Incident i = incidents.save(new Incident(req.title(), req.description(), users.getReferenceById(actor.id()),
                service, req.impact(), req.urgency(), req.affectedUsers(), now));
        publish(EventType.CREATED, i, actor, null, "%s created by %s".formatted(i.getNumber(), actor.label()), now);

        classify(i, req.categoryId(), actor, now);
        prioritize(i, Actor.SYSTEM, now);
        detectDuplicate(i, now);
        autoAssign(i, now);

        if (correlation.crossesMajorIncidentThreshold(service.getId())) {
            publish(EventType.MAJOR_INCIDENT, i, Actor.SYSTEM, null,
                    "%d+ incidents on %s within %d min - probable major incident".formatted(CorrelationEngine.MAJOR_INCIDENT_THRESHOLD,
                            service.getName(), CorrelationEngine.MAJOR_INCIDENT_WINDOW.toMinutes()), now);
        }
        return detail(i, actor);
    }

    private void classify(Incident i, Long categoryId, Actor actor, Instant now) {
        Category category;
        Double confidence = null;
        boolean auto = categoryId == null;
        if (!auto) {
            category = categories.findById(categoryId).orElseThrow(() -> new NotFoundException("Category " + categoryId + " not found"));
        } else {
            List<Category> active = categories.findByActiveTrueOrderByName();
            if (active.isEmpty()) throw new ValidationException("No incident categories are configured");
            var suggestions = classifier.suggest(i.getTitle(), i.getDescription(), i.getService(), active);
            if (!suggestions.isEmpty()) {
                Long id = suggestions.get(0).categoryId();
                category = active.stream().filter(c -> c.getId().equals(id)).findFirst().orElseThrow();
                confidence = suggestions.get(0).confidence();
            } else {
                category = i.getService().getDefaultCategory() != null ? i.getService().getDefaultCategory() : active.get(0);
                confidence = 0.0;
            }
        }
        IncidentStatus from = i.getStatus();
        i.classify(category, auto, confidence, now);
        publish(EventType.CLASSIFIED, i, auto ? Actor.SYSTEM : actor, from, auto
                ? "Incident auto-classified as %s (%.0f%% confidence)".formatted(category.getName().toUpperCase(), confidence * 100)
                : "Incident classified as %s by reporter".formatted(category.getName().toUpperCase()), now);
    }

    /** Computes priority and (re)applies SLA. Emits PRIORITIZED first time, PRIORITY_CHANGED on later changes. */
    private void prioritize(Incident i, Actor actor, Instant now) {
        Priority old = i.getPriority();
        var a = priorityEngine.assess(i.getImpact(), i.getUrgency(), i.getService().getCriticality(), i.getAffectedUsers());
        i.applyPriority(a.priority(), a.score(), a.explanation());
        SlaPolicy policy = i.getService().getSlaPolicy() != null ? i.getService().getSlaPolicy()
                : slaPolicies.findFirstByDefaultPolicyTrue().orElseThrow(() ->
                new MissingSlaConfigurationException("No SLA policy for service '" + i.getService().getName() + "' and no default policy configured"));
        i.applySla(policy);
        if (old == null) {
            publish(EventType.PRIORITIZED, i, actor, null, "Priority calculated as %s (%s), score %.1f".formatted(a.priority(), a.priority().label(), a.score()), now);
            publish(EventType.SLA_APPLIED, i, actor, null, "SLA '%s' applied: respond within %d min, resolve within %d min"
                    .formatted(policy.getName(), i.getResponseTargetMinutes(), i.getResolutionTargetMinutes()), now);
        } else if (old != a.priority()) {
            publish(EventType.PRIORITY_CHANGED, i, actor, null, "Priority changed from %s to %s; SLA deadlines recalculated".formatted(old, a.priority()), now);
        }
    }

    private void detectDuplicate(Incident i, Instant now) {
        var matches = similarity.findSimilarOpen(i.getTitle(), i.getDescription(), i.getService().getId(), i.getId(), 1);
        if (matches.isEmpty() || matches.get(0).score() < SimilarityService.DUPLICATE_THRESHOLD) return;
        Incident original = matches.get(0).incident();
        Incident root = original.getDuplicateOf() != null ? original.getDuplicateOf() : original;
        i.markPossibleDuplicateOf(root, matches.get(0).score());
        publish(EventType.DUPLICATE_DETECTED, i, Actor.SYSTEM, null,
                "Possible duplicate of %s \"%s\" (%.0f%% similar)".formatted(root.getNumber(), root.getTitle(), matches.get(0).score() * 100), now);
    }

    private void autoAssign(Incident i, Instant now) {
        if (!settings.getBool(SettingsService.AUTO_ASSIGN)) {
            publish(EventType.UNASSIGNED_QUEUED, i, Actor.SYSTEM, null, "Auto-assignment is off; waiting in the queue", now);
            return;
        }
        Technician chosen = null;
        String via = null;
        Incident root = i.getDuplicateOf();
        if (root != null && root.isOpen() && root.getAssignee() != null) {
            Technician t = root.getAssignee();
            if (t.canAccept(assignment.currentLoads().getOrDefault(t.getId(), 0L).intValue())) {
                chosen = t;
                via = "correlation with " + root.getNumber();
            }
        }
        if (chosen == null) {
            Optional<Technician> picked = assignment.autoSelect(i);
            if (picked.isPresent()) {
                chosen = picked.get();
                via = assignment.active().displayName() + " strategy";
            }
        }
        if (chosen == null) {
            publish(EventType.UNASSIGNED_QUEUED, i, Actor.SYSTEM, null, "No available technician with spare capacity - queued for manual assignment", now);
            return;
        }
        assign(i, chosen, Actor.SYSTEM, via, now);
    }

    private void assign(Incident i, Technician t, Actor actor, String via, Instant now) {
        IncidentStatus from = i.getStatus();
        i.assignTo(t, now);
        publish(EventType.ASSIGNED, i, actor, from, "Assigned to Technician %s (%s) via %s".formatted(t.getCode(), t.getFullName(), via), now);
    }

    // =============================== queries ===============================

    @Transactional(readOnly = true)
    public Detail get(Long id, Actor actor) {
        Incident i = load(id);
        ensureCanView(i, actor);
        return detail(i, actor);
    }

    @Transactional(readOnly = true)
    public Page<Summary> search(Query q, Pageable pageable, Actor actor) {
        if (actor.is(Role.EMPLOYEE)) {
            q = new Query(q.q(), q.status(), q.priority(), q.categoryId(), q.serviceId(), q.assigneeId(), actor.id(),
                    q.slaStatus(), q.breached(), q.open(), q.unassigned(), q.from(), q.to());
        }
        return incidents.findAll(IncidentSpecifications.of(q), pageable).map(Summary::of);
    }

    @Transactional(readOnly = true)
    public List<AuditView> timeline(Long id, Actor actor) {
        ensureCanView(load(id), actor);
        return audit.findByIncidentIdOrderByTimestampAscIdAsc(id).stream().map(AuditView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<Recommendation> recommendations(Long id, Actor actor) {
        Incident i = load(id);
        ensureStaff(actor);
        return assignment.recommend(i, 5).stream().map(r -> {
            Technician t = r.candidate().technician();
            return new Recommendation(t.getId(), t.getFullName(), r.score(), r.reason(), r.candidate().activeLoad(),
                    t.getMaxWorkload(), t.getExperienceYears(), t.getSkills().stream().map(Category::getName).sorted().toList());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<SimilarIncident> suggestedResolutions(Long id, Actor actor) {
        Incident i = load(id);
        ensureCanView(i, actor);
        Long cat = i.getCategory() == null ? null : i.getCategory().getId();
        return similarity.findSimilarResolved(i.getTitle(), i.getDescription(), cat, i.getId(), 3).stream().map(this::similar).toList();
    }

    @Transactional(readOnly = true)
    public List<SimilarIncident> similarOpen(Long id, Actor actor) {
        Incident i = load(id);
        ensureStaff(actor);
        return similarity.findSimilarOpen(i.getTitle(), i.getDescription(), i.getService().getId(), i.getId(), 5).stream().map(this::similar).toList();
    }

    private SimilarIncident similar(SimilarityService.Match m) {
        Incident i = m.incident();
        return new SimilarIncident(i.getId(), i.getNumber(), i.getTitle(), i.getStatus(), i.getService().getName(), m.score(),
                i.getResolution(), i.getAssignee() == null ? null : i.getAssignee().getFullName(), i.getCreatedAt());
    }

    // =============================== commands ===============================

    public Detail update(Long id, UpdateIncident req, Actor actor) {
        Incident i = load(id);
        Instant now = clock.instant();
        boolean detailsChange = req.title() != null || req.description() != null;
        boolean assessmentChange = req.impact() != null || req.urgency() != null || req.affectedUsers() != null
                || req.serviceId() != null || req.categoryId() != null;
        if (detailsChange) {
            ensure(IncidentAction.EDIT, i, actor);
            i.updateDetails(req.title(), req.description(), now);
            publish(EventType.UPDATED, i, actor, null, "Details updated by " + actor.label(), now);
        }
        if (assessmentChange) {
            ensure(IncidentAction.REASSESS, i, actor);
            ItService service = req.serviceId() == null ? null : services.findWithPolicyById(req.serviceId())
                    .orElseThrow(() -> new NotFoundException("Service " + req.serviceId() + " not found"));
            i.reassess(req.impact(), req.urgency(), req.affectedUsers(), service);
            if (req.categoryId() != null) {
                Category c = categories.findById(req.categoryId()).orElseThrow(() -> new NotFoundException("Category not found"));
                i.classify(c, false, null, now);
                publish(EventType.CLASSIFIED, i, actor, null, "Re-classified as " + c.getName().toUpperCase(), now);
            }
            publish(EventType.UPDATED, i, actor, null, "Impact %s, urgency %s, %d affected users, service %s".formatted(
                    i.getImpact(), i.getUrgency(), i.getAffectedUsers(), i.getService().getName()), now);
            prioritize(i, actor, now);
            i.evaluateSla(now, settings.getDouble(SettingsService.SLA_WARNING_THRESHOLD));
        }
        return detail(i, actor);
    }

    /** technicianId == null -> let the active strategy choose. */
    public Detail assign(Long id, Long technicianId, Actor actor) {
        Incident i = load(id);
        Instant now = clock.instant();
        ensure(IncidentAction.ASSIGN, i, actor);
        if (technicianId == null) {
            Technician t = assignment.autoSelect(i).orElseThrow(() ->
                    new TechnicianUnavailableException("No available technician has spare capacity right now"));
            assign(i, t, actor, assignment.active().displayName() + " strategy (requested by " + actor.name() + ")", now);
        } else {
            Technician t = technicians.findById(technicianId).orElseThrow(() -> new NotFoundException("Technician " + technicianId + " not found"));
            assignment.checkCanTake(t);
            assign(i, t, actor, "manual assignment by " + actor.label(), now);
        }
        return detail(i, actor);
    }

    /** A technician picks an unassigned incident from the queue. */
    public Detail take(Long id, Actor actor) {
        Incident i = load(id);
        ensure(IncidentAction.TAKE, i, actor);
        Technician t = technicians.findById(actor.id()).orElseThrow(() -> new NotFoundException("Technician not found"));
        assignment.checkCanTake(t);
        assign(i, t, actor, "self-assignment from queue", clock.instant());
        return detail(i, actor);
    }

    public Detail acknowledge(Long id, Actor actor) {
        return transition(id, actor, IncidentAction.ACKNOWLEDGE, EventType.ACKNOWLEDGED, (i, now) -> {
            i.acknowledge(now);
            return "Technician acknowledged incident" + (i.isResponseBreached() ? " (response SLA missed)" : " within response SLA");
        });
    }

    public Detail start(Long id, Actor actor) {
        return transition(id, actor, IncidentAction.START, EventType.WORK_STARTED, (i, now) -> {
            i.startWork(now);
            return "Investigation started";
        });
    }

    public Detail requestInfo(Long id, String question, Actor actor) {
        return transition(id, actor, IncidentAction.REQUEST_INFO, EventType.INFO_REQUESTED, (i, now) -> {
            i.addComment(users.getReferenceById(actor.id()), CommentType.INFO_REQUEST, question, now);
            i.requestInfo(now);
            return "Information requested from reporter (SLA clock paused): " + question;
        });
    }

    public Detail provideInfo(Long id, String answer, Actor actor) {
        return transition(id, actor, IncidentAction.PROVIDE_INFO, EventType.INFO_PROVIDED, (i, now) -> {
            i.addComment(users.getReferenceById(actor.id()), CommentType.INFO_RESPONSE, answer, now);
            i.provideInfo(now);
            return "Reporter provided information (SLA clock resumed)";
        });
    }

    public Detail resolve(Long id, String resolution, String rootCause, Actor actor) {
        return transition(id, actor, IncidentAction.RESOLVE, EventType.RESOLVED, (i, now) -> {
            i.resolve(resolution, rootCause, now);
            i.addComment(users.getReferenceById(actor.id()), CommentType.RESOLUTION, resolution, now);
            return "Resolution provided; incident marked RESOLVED" + (i.isResolutionBreached() ? " (after SLA deadline)" : " within SLA");
        });
    }

    public Detail verify(Long id, Actor actor) {
        return transition(id, actor, IncidentAction.VERIFY, EventType.VERIFIED, (i, now) -> {
            i.verify(now);
            return "Resolution verified by " + actor.label();
        });
    }

    public Detail reopen(Long id, String reason, Actor actor) {
        return transition(id, actor, IncidentAction.REOPEN, EventType.REOPENED, (i, now) -> {
            i.addComment(users.getReferenceById(actor.id()), CommentType.COMMENT, "Reopened: " + reason, now);
            i.reopen(now);
            return "Reopened: " + reason;
        });
    }

    public Detail close(Long id, String notes, Actor actor) {
        return transition(id, actor, IncidentAction.CLOSE, EventType.CLOSED, (i, now) -> {
            i.close(notes, now);
            return "Incident CLOSED" + (notes == null || notes.isBlank() ? "" : ": " + notes);
        });
    }

    public Detail cancel(Long id, String reason, Actor actor) {
        return transition(id, actor, IncidentAction.CANCEL, EventType.CANCELLED, (i, now) -> {
            i.cancel(reason, now);
            return "Cancelled: " + reason;
        });
    }

    /** Closes this incident as a duplicate of another (the root keeps all the work). */
    public Detail mergeInto(Long id, Long rootId, Actor actor) {
        if (Objects.equals(id, rootId)) throw new ValidationException("An incident cannot be a duplicate of itself");
        Incident root = load(rootId);
        return transition(id, actor, IncidentAction.MERGE, EventType.CANCELLED, (i, now) -> {
            Incident target = root.getDuplicateOf() != null ? root.getDuplicateOf() : root;
            i.markPossibleDuplicateOf(target, i.getDuplicateScore() == null ? 1.0 : i.getDuplicateScore());
            i.cancel("Duplicate of " + target.getNumber(), now);
            return "Merged as duplicate of " + target.getNumber();
        });
    }

    /** Reporter rates the fix; a RESOLVED incident is then verified and closed automatically. */
    public Detail feedback(Long id, int rating, String comment, Actor actor) {
        Incident i = load(id);
        Instant now = clock.instant();
        ensure(IncidentAction.FEEDBACK, i, actor);
        i.recordFeedback(rating, comment);
        publish(EventType.FEEDBACK, i, actor, null, "Feedback %d/5%s".formatted(rating, comment == null || comment.isBlank() ? "" : ": " + comment), now);
        if (i.getStatus() == IncidentStatus.RESOLVED) {
            IncidentStatus from = i.getStatus();
            i.verify(now);
            publish(EventType.VERIFIED, i, actor, from, "Resolution confirmed by reporter", now);
        }
        if (i.getStatus() == IncidentStatus.VERIFIED) {
            i.close("Closed after reporter feedback", now);
            publish(EventType.CLOSED, i, actor, IncidentStatus.VERIFIED, "Incident CLOSED after feedback", now);
        }
        return detail(i, actor);
    }

    public Detail comment(Long id, String text, boolean internal, Actor actor) {
        Incident i = load(id);
        Instant now = clock.instant();
        ensure(internal ? IncidentAction.NOTE : IncidentAction.COMMENT, i, actor);
        i.addComment(users.getReferenceById(actor.id()), internal ? CommentType.INVESTIGATION_NOTE : CommentType.COMMENT, text, now);
        publish(internal ? EventType.NOTE_ADDED : EventType.COMMENTED, i, actor, null,
                (internal ? "Investigation note: " : "Comment: ") + abbreviate(text), now);
        return detail(i, actor);
    }

    // =============================== helpers ===============================

    @FunctionalInterface
    private interface Step { String apply(Incident i, Instant now); }

    private Detail transition(Long id, Actor actor, IncidentAction action, EventType type, Step step) {
        Incident i = load(id);
        ensure(action, i, actor);
        Instant now = clock.instant();
        IncidentStatus from = i.getStatus();
        String detail = step.apply(i, now);
        publish(type, i, actor, from, detail, now);
        return detail(i, actor);
    }

    private Incident load(Long id) {
        return incidents.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Incident " + id + " not found"));
    }

    private IncidentAction.Relation relation(Incident i, Actor a) {
        return new IncidentAction.Relation(a.isAdmin(), a.is(Role.TECHNICIAN),
                i.getReporter().getId().equals(a.id()),
                i.getAssignee() != null && i.getAssignee().getId().equals(a.id()));
    }

    private void ensure(IncidentAction action, Incident i, Actor a) {
        ensureCanView(i, a);
        if (!action.permits(relation(i, a), i))
            throw new UnauthorizedOperationException("You are not allowed to " + action.name().toLowerCase().replace('_', ' ') + " " + i.getNumber());
        if (!action.applicable(i))
            throw new InvalidStateTransitionException("Cannot %s %s while it is %s".formatted(action.name().toLowerCase().replace('_', ' '), i.getNumber(), i.getStatus()));
    }

    private void ensureCanView(Incident i, Actor a) {
        if (a.is(Role.EMPLOYEE) && !i.getReporter().getId().equals(a.id()))
            throw new UnauthorizedOperationException("You can only access incidents you reported");
    }

    private void ensureStaff(Actor a) {
        if (a.is(Role.EMPLOYEE)) throw new UnauthorizedOperationException("Only IT staff can use this feature");
    }

    private void publish(EventType type, Incident i, Actor actor, IncidentStatus from, String detail, Instant now) {
        events.publishEvent(IncidentEvent.of(type, i, actor, from, detail, now));
    }

    private static String abbreviate(String s) { return s.length() > 140 ? s.substring(0, 139) + "…" : s; }

    Detail detail(Incident i, Actor actor) {
        IncidentAction.Relation rel = relation(i, actor);
        boolean employee = actor.is(Role.EMPLOYEE);
        List<String> actions = new ArrayList<>();
        for (IncidentAction a : IncidentAction.values()) if (a.permits(rel, i) && a.applicable(i)) actions.add(a.name());
        Instant now = clock.instant();
        Incident dup = i.getDuplicateOf();
        return new Detail(Summary.of(i), i.getDescription(), i.getImpact(), i.getUrgency(), i.getAffectedUsers(),
                i.getPriorityScore(), i.getPriorityReason(), i.isAutoCategorized(), i.getCategoryConfidence(),
                i.getSlaPolicyName(), i.getResponseTargetMinutes(), i.getResolutionTargetMinutes(),
                i.isResponseBreached(), i.isResolutionBreached(),
                i.getAcknowledgedAt() != null ? 0 : round(i.responseElapsedFraction(now)),
                i.isOpen() && i.getStatus().slaClockRunning() ? round(i.resolutionElapsedFraction(now)) : 0,
                i.getAcknowledgedAt(), i.getResolvedAt(), i.getClosedAt(),
                i.getResolution(), i.getRootCause(), i.getClosureNotes(), i.getFeedbackRating(), i.getFeedbackComment(),
                Person.of(i.getReporter()), Person.of(i.getAssignee()),
                dup == null ? null : new DuplicateRef(dup.getId(), dup.getNumber(), dup.getTitle(), i.getDuplicateScore()),
                i.getComments().stream().filter(c -> !(employee && c.isInternal())).map(CommentView::of).toList(),
                actions, i.getVersion());
    }

    private static double round(double v) { return Math.round(v * 1000) / 1000.0; }
}
