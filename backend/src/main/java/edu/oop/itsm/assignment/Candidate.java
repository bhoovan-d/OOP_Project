package edu.oop.itsm.assignment;

import edu.oop.itsm.domain.Incident;
import edu.oop.itsm.domain.Technician;

/** A technician plus the live facts strategies need, computed once per assignment in bulk queries. */
public record Candidate(Technician technician, long activeLoad, long resolvedOnService, long resolvedInCategory) {

    public double loadRatio() {
        return (double) activeLoad / technician.getMaxWorkload();
    }

    public boolean skilledFor(Incident incident) {
        return technician.hasSkill(incident.getCategory());
    }
}
