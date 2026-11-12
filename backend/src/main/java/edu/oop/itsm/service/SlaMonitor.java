package edu.oop.itsm.service;

import edu.oop.itsm.domain.*;
import edu.oop.itsm.event.Actor;
import edu.oop.itsm.event.IncidentEvent;
import edu.oop.itsm.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Background SLA watchdog: every tick it re-evaluates open incidents, raises warnings before deadlines,
 * records breaches, and escalates unacknowledged P1/P2 incidents up the chain
 * Technician -> Team Lead -> System Admin -> IT Manager (one level per elapsed response window).
 * ponytail: scans all open incidents each tick; at very high volume, query only those due within the warning window.
 */
@Component
public class SlaMonitor {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitor.class);

    private final IncidentRepository incidents;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SlaMonitor(IncidentRepository incidents, SettingsService settings, ApplicationEventPublisher events, Clock clock) {
        this.incidents = incidents;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${itsm.sla.monitor-interval-ms:30000}", initialDelayString = "${itsm.sla.monitor-initial-delay-ms:15000}")
    @Transactional
    public void tick() {
        Instant now = clock.instant();
        double warn = settings.getDouble(SettingsService.SLA_WARNING_THRESHOLD);
        boolean escalation = settings.getBool(SettingsService.ESCALATION_ENABLED);
        int changes = 0;
        for (Incident i : incidents.findByStatusIn(IncidentStatus.openStates())) {
            changes += evaluate(i, now, warn, escalation);
        }
        if (changes > 0) log.info("SLA monitor: {} change(s)", changes);
    }

    int evaluate(Incident i, Instant now, double warn, boolean escalation) {
        int changes = 0;
        SlaStatus before = i.getSlaStatus();
        boolean responseBefore = i.isResponseBreached(), resolutionBefore = i.isResolutionBreached();
        SlaStatus after = i.evaluateSla(now, warn);

        if (after == SlaStatus.AT_RISK && before != SlaStatus.AT_RISK) {
            publish(EventType.SLA_WARNING, i, "SLA approaching: %.0f%% of the window used, resolution due %s"
                    .formatted(Math.max(i.responseElapsedFraction(now) * (i.getAcknowledgedAt() == null ? 1 : 0), i.resolutionElapsedFraction(now)) * 100,
                            i.getResolutionDueAt()), now);
            changes++;
        }
        if (i.isResponseBreached() && !responseBefore) {
            publish(EventType.SLA_BREACHED, i, "Response SLA violated: not acknowledged within %d min".formatted(i.getResponseTargetMinutes()), now);
            changes++;
        }
        if (i.isResolutionBreached() && !resolutionBefore) {
            publish(EventType.SLA_BREACHED, i, "Resolution SLA violated: not resolved within %d min".formatted(i.getResolutionTargetMinutes()), now);
            changes++;
        }

        if (escalation && i.getPriority() != null && i.getPriority().isUrgent() && i.getAcknowledgedAt() == null
                && i.getResponseDueAt() != null && now.isAfter(i.getResponseDueAt())) {
            long overdueMin = Duration.between(i.getResponseDueAt(), now).toMinutes();
            int target = (int) Math.min(EscalationLevel.IT_MANAGER.ordinal(), 1 + overdueMin / Math.max(1, i.getResponseTargetMinutes()));
            while (i.getEscalationLevel().ordinal() < target) {
                EscalationLevel level = i.escalate(now);
                publish(EventType.ESCALATED, i, "Not acknowledged %d min after response deadline - escalated to %s"
                        .formatted(overdueMin, level.name().replace('_', ' ')), now);
                changes++;
            }
        }
        return changes;
    }

    private void publish(EventType type, Incident i, String detail, Instant now) {
        events.publishEvent(IncidentEvent.of(type, i, Actor.SYSTEM, null, detail, now));
    }
}
