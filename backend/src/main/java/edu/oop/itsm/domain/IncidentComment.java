package edu.oop.itsm.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Part of the Incident aggregate (composition: lives and dies with its incident). */
@Entity
@Table(name = "incident_comments", indexes = @Index(name = "ix_comment_incident", columnList = "incident_id"))
public class IncidentComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommentType type;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    protected IncidentComment() {}

    IncidentComment(Incident incident, User author, CommentType type, String body, Instant createdAt) {
        this.incident = incident;
        this.author = author;
        this.type = type;
        this.body = body;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Incident getIncident() { return incident; }
    public User getAuthor() { return author; }
    public CommentType getType() { return type; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isInternal() { return type.isInternal(); }
}
