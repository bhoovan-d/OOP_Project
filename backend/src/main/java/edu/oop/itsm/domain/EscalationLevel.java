package edu.oop.itsm.domain;

/** Escalation chain for unacknowledged urgent incidents: Technician -> Team Lead -> System Admin -> IT Manager. */
public enum EscalationLevel {
    NONE, TEAM_LEAD, SYSTEM_ADMIN, IT_MANAGER;

    public EscalationLevel next() {
        return this == IT_MANAGER ? IT_MANAGER : values()[ordinal() + 1];
    }

    public boolean isTop() { return this == IT_MANAGER; }
}
