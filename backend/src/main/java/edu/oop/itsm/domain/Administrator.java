package edu.oop.itsm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("ADMIN")
public class Administrator extends User {

    /** IT Managers sit at the top of the escalation chain; other admins are system administrators. */
    private boolean itManager;

    protected Administrator() {}

    public Administrator(String fullName, String email, String passwordHash, boolean itManager) {
        super(fullName, email, passwordHash);
        this.itManager = itManager;
    }

    @Override public Role getRole() { return Role.ADMIN; }

    public boolean isItManager() { return itManager; }
    public void setItManager(boolean itManager) { this.itManager = itManager; }
}
