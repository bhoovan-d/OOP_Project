package edu.oop.itsm.domain;

/** Business criticality of an IT service. */
public enum Criticality {
    LOW(1), MODERATE(2), HIGH(3), CRITICAL(4), MISSION_CRITICAL(5);

    private final int weight;

    Criticality(int weight) { this.weight = weight; }

    public int weight() { return weight; }
}
