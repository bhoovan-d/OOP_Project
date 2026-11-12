package edu.oop.itsm.domain;

import jakarta.persistence.*;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Incident categories are data, not code: admins add new ones (with classifier keywords) at runtime
 * and nothing else in the system has to change.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(length = 255)
    private String description;

    @ElementCollection
    @CollectionTable(name = "category_keywords", joinColumns = @JoinColumn(name = "category_id"))
    @Column(name = "keyword", length = 40)
    private Set<String> keywords = new LinkedHashSet<>();

    private boolean active = true;

    protected Category() {}

    public Category(String name, String description, Collection<String> keywords) {
        this.name = name;
        this.description = description;
        setKeywords(keywords);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Set<String> getKeywords() { return Collections.unmodifiableSet(keywords); }
    public boolean isActive() { return active; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setActive(boolean active) { this.active = active; }

    public void setKeywords(Collection<String> keywords) {
        this.keywords.clear();
        if (keywords != null) keywords.stream().map(k -> k.trim().toLowerCase()).filter(k -> !k.isEmpty()).forEach(this.keywords::add);
    }
}
