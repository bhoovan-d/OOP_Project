package edu.oop.itsm.domain;

/** How quickly the business needs a fix. Weight feeds the priority score. */
public enum Urgency {
    LOW(1, "Can wait"), MEDIUM(2, "Work degraded"), HIGH(3, "Work blocked"), CRITICAL(4, "Revenue / safety at stake");

    private final int weight;
    private final String label;

    Urgency(int weight, String label) { this.weight = weight; this.label = label; }

    public int weight() { return weight; }
    public String label() { return label; }
}
