package edu.oop.itsm.event;

import edu.oop.itsm.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real-time push over Server-Sent Events. Observer #3: after each committed incident event, staff dashboards
 * and the reporter get a live update; in-app notifications are pushed to their recipient.
 * ponytail: subscribers live in this JVM; put Redis pub/sub between nodes when scaling horizontally.
 */
@Component
public class LiveEventHub {

    private static final Logger log = LoggerFactory.getLogger(LiveEventHub.class);

    private record Subscriber(Long userId, Role role, SseEmitter emitter) {}

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(Long userId, Role role) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        Subscriber s = new Subscriber(userId, role, emitter);
        subscribers.add(s);
        emitter.onCompletion(() -> subscribers.remove(s));
        emitter.onTimeout(() -> subscribers.remove(s));
        emitter.onError(t -> subscribers.remove(s));
        send(s, "hello", Map.of("connected", true));
        return emitter;
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void on(IncidentEvent e) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", e.type());
        payload.put("incidentId", e.incidentId());
        payload.put("number", e.number());
        payload.put("title", e.title());
        payload.put("priority", e.priority());
        payload.put("status", e.to());
        payload.put("actor", e.actorName());
        payload.put("detail", e.detail());
        payload.put("service", e.serviceName());
        payload.put("at", e.at().toString());
        for (Subscriber s : subscribers) {
            if (s.role() != Role.EMPLOYEE || s.userId().equals(e.reporterId())) send(s, "incident", payload);
        }
    }

    public void pushToUser(Long userId, String eventName, Object data) {
        for (Subscriber s : subscribers) if (s.userId().equals(userId)) send(s, eventName, data);
    }

    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        for (Subscriber s : subscribers) send(s, "ping", Map.of());
    }

    public int connectedClients() { return subscribers.size(); }

    private void send(Subscriber s, String name, Object data) {
        try {
            s.emitter().send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException ex) {
            subscribers.remove(s);
            log.debug("Dropped SSE subscriber {}: {}", s.userId(), ex.getMessage());
        }
    }
}
