package edu.oop.itsm.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Abstract base for every person using the system. Employee, Technician and Administrator are
 * genuine "is-a" specialisations, stored in one table (SINGLE_TABLE) discriminated by role.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role", discriminatorType = DiscriminatorType.STRING, length = 20)
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 30)
    private String phone;

    @Column(length = 60)
    private String department;

    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {}

    protected User(String fullName, String email, String passwordHash) {
        this.fullName = Objects.requireNonNull(fullName);
        this.email = Objects.requireNonNull(email).trim().toLowerCase();
        this.passwordHash = Objects.requireNonNull(passwordHash);
    }

    /** Polymorphic: each subtype knows its own role. */
    public abstract Role getRole();

    /** Human-friendly code such as E102 / T205 used in audit lines. */
    public String getCode() { return getRole().codePrefix() + (100 + id); }

    public boolean isAdmin() { return getRole() == Role.ADMIN; }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public String getPhone() { return phone; }
    public String getDepartment() { return department; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }

    public void setFullName(String fullName) { this.fullName = Objects.requireNonNull(fullName); }
    public void setPhone(String phone) { this.phone = phone; }
    public void setDepartment(String department) { this.department = department; }
    public void setActive(boolean active) { this.active = active; }
    public void changePasswordHash(String passwordHash) { this.passwordHash = Objects.requireNonNull(passwordHash); }

    @Override public boolean equals(Object o) { return o instanceof User u && id != null && id.equals(u.id); }
    @Override public int hashCode() { return User.class.hashCode(); }
}
