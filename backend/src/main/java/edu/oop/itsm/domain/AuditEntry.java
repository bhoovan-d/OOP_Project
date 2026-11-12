package edu.oop.itsm.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Immutable audit record. Written by the audit observer, never updated. */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "ix_audit_incident", columnList = "incidentId"),
        @Index(name = "ix_audit_time", columnList = "timestamp")})
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    private Long actorId;

    @Column(length = 120)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventType action;

    private Long incidentId;

    @Column(length = 20)
    private String incidentNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IncidentStatus fromState;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IncidentStatus toState;

    @Column(length = 1000)
    private String description;

    protected AuditEntry() {}

    public AuditEntry(Instant timestamp, Long actorId, String actorName, EventType action, Long incidentId,
                      String incidentNumber, IncidentStatus fromState, IncidentStatus toState, String description) {
        this.timestamp = timestamp;
        this.actorId = actorId;
        this.actorName = actorName;
        this.action = action;
        this.incidentId = incidentId;
        this.incidentNumber = incidentNumber;
        this.fromState = fromState;
        this.toState = toState;
        this.description = description != null && description.length() > 1000 ? description.substring(0, 1000) : description;
    }

    public Long getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public Long getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public EventType getAction() { return action; }
    public Long getIncidentId() { return incidentId; }
    public String getIncidentNumber() { return incidentNumber; }
    public IncidentStatus getFromState() { return fromState; }
    public IncidentStatus getToState() { return toState; }
    public String getDescription() { return description; }
}
