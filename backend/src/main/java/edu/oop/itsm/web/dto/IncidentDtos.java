package edu.oop.itsm.web.dto;

import edu.oop.itsm.domain.*;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Request/response shapes for the incident API. Entities never leave the service layer. */
public final class IncidentDtos {

    private IncidentDtos() {}

    // ---------------- requests ----------------

    public record CreateIncident(
            @NotBlank @Size(min = 5, max = 200) String title,
            @NotBlank @Size(min = 10, max = 4000) String description,
            @NotNull Long serviceId,
            Long categoryId,
            @NotNull Impact impact,
            @NotNull Urgency urgency,
            @Min(1) @Max(1_000_000) int affectedUsers) {}

    public record UpdateIncident(
            @Size(min = 5, max = 200) String title,
            @Size(min = 10, max = 4000) String description,
            Long serviceId, Long categoryId, Impact impact, Urgency urgency,
            @Min(1) @Max(1_000_000) Integer affectedUsers) {}

    public record AssignRequest(Long technicianId) {}

    public record TextRequest(@NotBlank @Size(max = 4000) String text) {}

    public record OptionalTextRequest(@Size(max = 4000) String text) {}

    public record CommentRequest(@NotBlank @Size(max = 4000) String text, boolean internal) {}

    public record ResolveRequest(@NotBlank @Size(min = 5, max = 4000) String resolution, @Size(max = 1000) String rootCause) {}

    public record FeedbackRequest(@Min(1) @Max(5) int rating, @Size(max = 1000) String comment) {}

    public record MergeRequest(@NotNull Long duplicateOfId) {}

    public record Query(
            String q, Set<IncidentStatus> status, Set<Priority> priority, Long categoryId, Long serviceId,
            Long assigneeId, Long reporterId, SlaStatus slaStatus, Boolean breached, Boolean open, Boolean unassigned,
            Instant from, Instant to) {}

    // ---------------- responses ----------------

    public record Ref(Long id, String name) {
        public static Ref of(Category c) { return c == null ? null : new Ref(c.getId(), c.getName()); }
        public static Ref of(ItService s) { return s == null ? null : new Ref(s.getId(), s.getName()); }
        public static Ref of(User u) { return u == null ? null : new Ref(u.getId(), u.getFullName()); }
    }

    public record Summary(
            Long id, String number, String title, IncidentStatus status, Priority priority,
            Ref category, Ref service, Ref assignee, Ref reporter,
            SlaStatus slaStatus, boolean breached, EscalationLevel escalationLevel,
            Instant responseDueAt, Instant resolutionDueAt, Instant createdAt, Instant updatedAt,
            String duplicateOfNumber) {

        public static Summary of(Incident i) {
            return new Summary(i.getId(), i.getNumber(), i.getTitle(), i.getStatus(), i.getPriority(),
                    Ref.of(i.getCategory()), Ref.of(i.getService()), Ref.of(i.getAssignee()), Ref.of(i.getReporter()),
                    i.getSlaStatus(), i.isBreached(), i.getEscalationLevel(),
                    i.getResponseDueAt(), i.getResolutionDueAt(), i.getCreatedAt(), i.getUpdatedAt(),
                    i.getDuplicateOf() == null ? null : i.getDuplicateOf().getNumber());
        }
    }

    public record CommentView(Long id, String author, Role authorRole, CommentType type, String body, boolean internal, Instant createdAt) {
        public static CommentView of(IncidentComment c) {
            return new CommentView(c.getId(), c.getAuthor().getFullName(), c.getAuthor().getRole(), c.getType(), c.getBody(), c.isInternal(), c.getCreatedAt());
        }
    }

    public record Person(Long id, String name, String email, String department, String phone) {
        public static Person of(User u) { return u == null ? null : new Person(u.getId(), u.getFullName(), u.getEmail(), u.getDepartment(), u.getPhone()); }
    }

    public record DuplicateRef(Long id, String number, String title, Double score) {}

    public record Detail(
            Summary summary, String description, Impact impact, Urgency urgency, int affectedUsers,
            double priorityScore, String priorityReason, boolean autoCategorized, Double categoryConfidence,
            String slaPolicyName, int responseTargetMinutes, int resolutionTargetMinutes,
            boolean responseBreached, boolean resolutionBreached,
            double responseElapsed, double resolutionElapsed,
            Instant acknowledgedAt, Instant resolvedAt, Instant closedAt,
            String resolution, String rootCause, String closureNotes, Integer feedbackRating, String feedbackComment,
            Person reporter, Person assignee, DuplicateRef duplicateOf,
            List<CommentView> comments, List<String> allowedActions, Long version) {}

    public record AuditView(Long id, Instant timestamp, String actor, EventType action, Long incidentId, String incidentNumber,
                            IncidentStatus fromState, IncidentStatus toState, String description) {
        public static AuditView of(AuditEntry a) {
            return new AuditView(a.getId(), a.getTimestamp(), a.getActorName(), a.getAction(), a.getIncidentId(), a.getIncidentNumber(),
                    a.getFromState(), a.getToState(), a.getDescription());
        }
    }

    public record Recommendation(Long technicianId, String name, double score, String reason, long activeLoad, int maxWorkload,
                                 int experienceYears, List<String> skills) {}

    public record SimilarIncident(Long id, String number, String title, IncidentStatus status, String service, double score,
                                  String resolution, String assignee, Instant createdAt) {}
}
