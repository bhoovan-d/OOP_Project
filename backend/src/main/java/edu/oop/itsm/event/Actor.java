package edu.oop.itsm.event;

import edu.oop.itsm.domain.Role;

/** Who performed an action. Built straight from the JWT, so most requests never load the user row. */
public record Actor(Long id, String name, Role role) {

    public static final Actor SYSTEM = new Actor(null, "System", null);

    public boolean is(Role r) { return role == r; }

    public boolean isAdmin() { return role == Role.ADMIN; }

    /** "Employee E102 (Rohan Gupta)" style label for audit lines. */
    public String label() {
        if (id == null) return name;
        String kind = switch (role) { case EMPLOYEE -> "Employee"; case TECHNICIAN -> "Technician"; case ADMIN -> "Admin"; };
        return "%s %s%d (%s)".formatted(kind, role.codePrefix(), 100 + id, name);
    }
}
