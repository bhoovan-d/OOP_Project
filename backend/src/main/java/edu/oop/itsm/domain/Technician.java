package edu.oop.itsm.domain;

import edu.oop.itsm.exception.ValidationException;
import jakarta.persistence.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@DiscriminatorValue("TECHNICIAN")
public class Technician extends User {

    /** Aggregation: categories exist independently of the technician. */
    @ManyToMany
    @JoinTable(name = "technician_skills",
            joinColumns = @JoinColumn(name = "technician_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> skills = new HashSet<>();

    private int maxWorkload = 5;
    private int experienceYears;
    private boolean available = true;
    private boolean teamLead;

    protected Technician() {}

    public Technician(String fullName, String email, String passwordHash) {
        super(fullName, email, passwordHash);
    }

    @Override public Role getRole() { return Role.TECHNICIAN; }

    public boolean hasSkill(Category category) {
        return category != null && skills.stream().anyMatch(s -> s.getId().equals(category.getId()));
    }

    /** Can this technician take one more incident right now? */
    public boolean canAccept(int currentLoad) {
        return isActive() && available && currentLoad < maxWorkload;
    }

    public Set<Category> getSkills() { return Collections.unmodifiableSet(skills); }
    public int getMaxWorkload() { return maxWorkload; }
    public int getExperienceYears() { return experienceYears; }
    public boolean isAvailable() { return available; }
    public boolean isTeamLead() { return teamLead; }

    public void setSkills(Set<Category> skills) { this.skills = new HashSet<>(skills); }
    public void setAvailable(boolean available) { this.available = available; }
    public void setTeamLead(boolean teamLead) { this.teamLead = teamLead; }

    public void setMaxWorkload(int maxWorkload) {
        if (maxWorkload < 1 || maxWorkload > 50) throw new ValidationException("Max workload must be between 1 and 50");
        this.maxWorkload = maxWorkload;
    }

    public void setExperienceYears(int experienceYears) {
        if (experienceYears < 0 || experienceYears > 60) throw new ValidationException("Experience must be between 0 and 60 years");
        this.experienceYears = experienceYears;
    }
}
