package edu.oop.itsm.domain;

import jakarta.persistence.*;

/** A business IT service that incidents are raised against (Payment Gateway, VPN, ...). */
@Entity
@Table(name = "it_services")
public class ItService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Criticality criticality = Criticality.MODERATE;

    /** Association: null means "use the default SLA policy". */
    @ManyToOne(fetch = FetchType.LAZY)
    private SlaPolicy slaPolicy;

    /** Hint for the auto-classifier. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Category defaultCategory;

    private int userBase;
    private boolean active = true;

    protected ItService() {}

    public ItService(String name, String description, Criticality criticality) {
        this.name = name;
        this.description = description;
        this.criticality = criticality;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Criticality getCriticality() { return criticality; }
    public SlaPolicy getSlaPolicy() { return slaPolicy; }
    public Category getDefaultCategory() { return defaultCategory; }
    public int getUserBase() { return userBase; }
    public boolean isActive() { return active; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setCriticality(Criticality criticality) { this.criticality = criticality; }
    public void setSlaPolicy(SlaPolicy slaPolicy) { this.slaPolicy = slaPolicy; }
    public void setDefaultCategory(Category defaultCategory) { this.defaultCategory = defaultCategory; }
    public void setUserBase(int userBase) { this.userBase = Math.max(0, userBase); }
    public void setActive(boolean active) { this.active = active; }
}
