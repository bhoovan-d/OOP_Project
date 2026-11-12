package edu.oop.itsm.domain;

import edu.oop.itsm.exception.MissingSlaConfigurationException;
import jakarta.persistence.*;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** An SLA contract: one {@link SlaTarget} per priority (composition via an element collection). */
@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(length = 255)
    private String description;

    /** Used for services that have no explicit policy. */
    private boolean defaultPolicy;

    @ElementCollection
    @CollectionTable(name = "sla_targets", joinColumns = @JoinColumn(name = "policy_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "priority", length = 4)
    private Map<Priority, SlaTarget> targets = new EnumMap<>(Priority.class);

    protected SlaPolicy() {}

    public SlaPolicy(String name, String description, Map<Priority, SlaTarget> targets) {
        this.name = name;
        this.description = description;
        setTargets(targets);
    }

    public SlaTarget targetFor(Priority priority) {
        SlaTarget t = targets.get(priority);
        if (t == null) throw new MissingSlaConfigurationException("SLA policy '" + name + "' has no target for " + priority);
        return t;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isDefaultPolicy() { return defaultPolicy; }
    public Map<Priority, SlaTarget> getTargets() { return Collections.unmodifiableMap(targets); }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setDefaultPolicy(boolean defaultPolicy) { this.defaultPolicy = defaultPolicy; }

    public void setTargets(Map<Priority, SlaTarget> newTargets) {
        for (Priority p : Priority.values()) {
            if (newTargets == null || !newTargets.containsKey(p))
                throw new MissingSlaConfigurationException("SLA policy must define targets for every priority (missing " + p + ")");
        }
        targets.clear();
        targets.putAll(newTargets);
    }
}
