package edu.oop.itsm.assignment;

import edu.oop.itsm.domain.Incident;
import edu.oop.itsm.domain.Technician;

import java.util.List;

/**
 * Strategy pattern: interchangeable technician-assignment algorithms.
 * Spring discovers every implementation; the admin picks the active one at runtime,
 * and adding a new algorithm is one new class — nothing else changes.
 */
public interface AssignmentStrategy {

    /** Stable identifier persisted in settings. */
    String key();

    String displayName();

    String description();

    /** Ranks candidates best-first. Candidates are already filtered for availability and spare capacity. */
    List<RankedTechnician> rank(Incident incident, List<Candidate> candidates);

    /** Hook for stateful strategies (round-robin) once an assignment really happens. */
    default void onAssigned(Technician technician) {}
}
