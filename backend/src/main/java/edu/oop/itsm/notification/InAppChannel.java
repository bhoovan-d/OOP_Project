package edu.oop.itsm.notification;

import edu.oop.itsm.domain.ChannelType;
import edu.oop.itsm.domain.Notification;
import edu.oop.itsm.domain.User;
import edu.oop.itsm.event.LiveEventHub;
import edu.oop.itsm.repository.NotificationRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Stores the notification for the bell menu and pushes it instantly over SSE. */
@Component
public class InAppChannel implements NotificationChannel {

    private final NotificationRepository repository;
    private final LiveEventHub hub;

    public InAppChannel(NotificationRepository repository, LiveEventHub hub) {
        this.repository = repository;
        this.hub = hub;
    }

    @Override public ChannelType type() { return ChannelType.IN_APP; }

    @Override
    public void deliver(Notification n, User recipient) {
        repository.save(n);
        hub.pushToUser(recipient.getId(), "notification", Map.of(
                "id", n.getId(), "title", n.getTitle(), "message", n.getMessage(),
                "type", n.getType(), "incidentId", n.getIncidentId() == null ? "" : n.getIncidentId(),
                "createdAt", n.getCreatedAt().toString()));
    }
}
