package edu.oop.itsm.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** One delivered message on one channel. In-app ones power the bell; email/SMS ones form the outbox log. */
@Entity
@Table(name = "notifications", indexes = @Index(name = "ix_notif_recipient", columnList = "recipientId,channel,readFlag"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long recipientId;

    @Column(length = 120)
    private String recipientAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChannelType channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventType type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 1000)
    private String message;

    private Long incidentId;

    @Column(name = "readFlag")
    private boolean read;

    @Column(length = 60)
    private String externalId;

    @Column(nullable = false)
    private Instant createdAt;

    protected Notification() {}

    public Notification(Long recipientId, String recipientAddress, ChannelType channel, EventType type,
                        String title, String message, Long incidentId, Instant createdAt) {
        this.recipientId = recipientId;
        this.recipientAddress = recipientAddress;
        this.channel = channel;
        this.type = type;
        this.title = title;
        this.message = message;
        this.incidentId = incidentId;
        this.createdAt = createdAt;
    }

    public void markRead() { this.read = true; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public Long getId() { return id; }
    public Long getRecipientId() { return recipientId; }
    public String getRecipientAddress() { return recipientAddress; }
    public ChannelType getChannel() { return channel; }
    public EventType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Long getIncidentId() { return incidentId; }
    public boolean isRead() { return read; }
    public String getExternalId() { return externalId; }
    public Instant getCreatedAt() { return createdAt; }
}
