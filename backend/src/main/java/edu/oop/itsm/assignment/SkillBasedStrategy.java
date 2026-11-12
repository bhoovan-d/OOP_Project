package edu.oop.itsm.assignment;

import edu.oop.itsm.domain.Incident;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Only technicians skilled in the incident's category; the least busy of them wins. */
@Component
public class SkillBasedStrategy implements AssignmentStrategy {

    @Override public String key() { return "skill"; }
    @Override public String displayName() { return "Skill-based"; }
    @Override public String description() { return "Only technicians skilled in the category; least busy first."; }

    @Override
    public List<RankedTechnician> rank(Incident incident, List<Candidate> candidates) {
        return candidates.stream()
                .filter(c -> c.skilledFor(incident))
                .sorted(Comparator.comparingDouble(Candidate::loadRatio)
                        .thenComparing(c -> -c.technician().getExperienceYears()))
                .map(c -> new RankedTechnician(c, round(1 - c.loadRatio()),
                        "Skilled in %s, load %d/%d".formatted(incident.getCategory().getName(), c.activeLoad(), c.technician().getMaxWorkload())))
                .toList();
    }

    static double round(double v) { return Math.round(v * 100) / 100.0; }
}
