package edu.oop.itsm.notification;

import edu.oop.itsm.domain.ChannelType;
import edu.oop.itsm.domain.Notification;
import edu.oop.itsm.domain.User;
import edu.oop.itsm.event.IncidentEvent;
import org.springframework.stereotype.Component;

/**
 * Factory pattern: builds the right notification for an event + channel. Callers never assemble
 * titles/bodies themselves; SMS gets a 160-char compact text, email/in-app get the full message.
 */
@Component
public class NotificationFactory {

    public Notification create(IncidentEvent e, User recipient, ChannelType channel) {
        String title = title(e);
        String message = switch (channel) {
            case SMS -> truncate("[ITSM] %s %s: %s".formatted(e.number(), e.priority() == null ? "" : e.priority(), title), 160);
            case EMAIL -> "Hello %s,%n%n%s%n%nIncident: %s - %s%nService: %s%nStatus: %s%n%n— I-ITSM"
                    .formatted(recipient.getFullName(), body(e), e.number(), e.title(), e.serviceName(), e.to());
            case IN_APP -> body(e);
        };
        String address = switch (channel) {
            case EMAIL -> recipient.getEmail();
            case SMS -> recipient.getPhone();
            case IN_APP -> null;
        };
        return new Notification(recipient.getId(), address, channel, e.type(), truncate(title, 160), truncate(message, 1000), e.incidentId(), e.at());
    }

    private String title(IncidentEvent e) {
        return switch (e.type()) {
            case CREATED -> e.number() + " logged";
            case ASSIGNED -> e.number() + " assigned" + (e.priority() != null ? " (" + e.priority() + ")" : "");
            case UNASSIGNED_QUEUED -> e.number() + " needs a technician";
            case PRIORITY_CHANGED -> e.number() + " priority changed to " + e.priority();
            case INFO_REQUESTED -> "More information needed on " + e.number();
            case INFO_PROVIDED -> "Reporter replied on " + e.number();
            case COMMENTED -> "New comment on " + e.number();
            case RESOLVED -> e.number() + " resolved";
            case REOPENED -> e.number() + " reopened";
            case CLOSED -> e.number() + " closed";
            case CANCELLED -> e.number() + " cancelled";
            case SLA_WARNING -> "SLA at risk: " + e.number();
            case SLA_BREACHED -> "SLA breached: " + e.number();
            case ESCALATED -> "Escalated to you: " + e.number();
            case MAJOR_INCIDENT -> "Major incident on " + e.serviceName();
            case DUPLICATE_DETECTED -> "Possible duplicate: " + e.number();
            case FEEDBACK -> "Feedback received on " + e.number();
            default -> e.number() + " updated";
        };
    }

    private String body(IncidentEvent e) {
        String detail = e.detail() == null ? "" : e.detail();
        return "%s — %s".formatted(e.title(), detail);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
