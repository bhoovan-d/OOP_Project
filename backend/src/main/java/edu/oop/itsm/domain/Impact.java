package edu.oop.itsm.domain;

/** How widely the business is affected. Weight feeds the priority score. */
public enum Impact {
    LOW(1, "Single user"), MEDIUM(2, "Team / department"), HIGH(3, "Multiple departments"), EXTENSIVE(4, "Whole organisation / customers");

    private final int weight;
    private final String label;

    Impact(int weight, String label) { this.weight = weight; this.label = label; }

    public int weight() { return weight; }
    public String label() { return label; }
}
