package edu.oop.itsm.event;

import edu.oop.itsm.domain.AuditEntry;
import edu.oop.itsm.repository.AuditRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer #1: writes every incident event to the audit log.
 * Runs synchronously inside the business transaction, so a change and its audit record commit or roll back together.
 */
@Component
public class AuditTrailListener {

    private final AuditRepository audit;

    public AuditTrailListener(AuditRepository audit) {
        this.audit = audit;
    }

    @EventListener
    public void on(IncidentEvent e) {
        audit.save(new AuditEntry(e.at(), e.actorId(), e.actorName(), e.type(), e.incidentId(), e.number(),
                e.from(), e.to(), e.detail()));
    }
}
