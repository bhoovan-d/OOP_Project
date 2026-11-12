package edu.oop.itsm.assignment;

import edu.oop.itsm.domain.Incident;
import edu.oop.itsm.domain.Priority;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Smart recommendation: weighted score over skill, spare capacity, experience and track record
 * with the same service/category. Urgent incidents shift weight towards experience.
 * <pre>
 *   score = 0.40 skill + 0.25 capacity + 0.15 experience + 0.20 history   (P3/P4)
 *   score = 0.40 skill + 0.15 capacity + 0.30 experience + 0.15 history   (P1/P2)
 * </pre>
 */
@Component
public class SmartStrategy implements AssignmentStrategy {

    @Override public String key() { return "smart"; }
    @Override public String displayName() { return "Smart (weighted)"; }
    @Override public String description() { return "Weighs skill, capacity, experience and past success with this service."; }

    @Override
    public List<RankedTechnician> rank(Incident incident, List<Candidate> candidates) {
        boolean urgent = incident.getPriority() != null && incident.getPriority().isUrgent();
        double wSkill = 0.40, wCap = urgent ? 0.15 : 0.25, wExp = urgent ? 0.30 : 0.15, wHist = urgent ? 0.15 : 0.20;

        return candidates.stream().map(c -> {
                    double skill = c.skilledFor(incident) ? 1 : 0;
                    double capacity = 1 - c.loadRatio();
                    double experience = Math.min(c.technician().getExperienceYears(), 10) / 10.0;
                    double history = Math.min(c.resolvedOnService() * 2 + c.resolvedInCategory(), 10) / 10.0;
                    double lead = incident.getPriority() == Priority.P1 && c.technician().isTeamLead() ? 0.05 : 0;
                    double score = wSkill * skill + wCap * capacity + wExp * experience + wHist * history + lead;
                    String reason = "skill %.0f, capacity %.0f%%, %dy exp, %d similar fixes".formatted(
                            skill, capacity * 100, c.technician().getExperienceYears(), c.resolvedOnService() + c.resolvedInCategory());
                    return new RankedTechnician(c, SkillBasedStrategy.round(Math.min(1, score)), reason);
                })
                .sorted(Comparator.comparingDouble(RankedTechnician::score).reversed())
                .toList();
    }
}
