package edu.oop.itsm.event;

import edu.oop.itsm.domain.*;

import java.time.Instant;

/**
 * Immutable snapshot of something that happened to an incident (Observer pattern "subject notification").
 * Carries plain values, not entities, so listeners on other threads never touch a closed persistence context.
 */
public record IncidentEvent(
        EventType type, Long incidentId, String number, String title, Priority priority,
        IncidentStatus from, IncidentStatus to, Long actorId, String actorName, String detail,
        Long reporterId, Long assigneeId, Long serviceId, String serviceName,
        EscalationLevel escalation, Instant at) {

    public static IncidentEvent of(EventType type, Incident i, Actor actor, IncidentStatus from, String detail, Instant at) {
        return new IncidentEvent(type, i.getId(), i.getNumber(), i.getTitle(), i.getPriority(), from, i.getStatus(),
                actor.id(), actor.name(), detail, i.getReporter().getId(),
                i.getAssignee() == null ? null : i.getAssignee().getId(),
                i.getService().getId(), i.getService().getName(), i.getEscalationLevel(), at);
    }
}
