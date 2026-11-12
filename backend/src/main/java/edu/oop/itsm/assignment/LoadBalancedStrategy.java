package edu.oop.itsm.assignment;

import edu.oop.itsm.domain.Incident;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Spreads work evenly: lowest utilisation first, skill only breaks ties. */
@Component
public class LoadBalancedStrategy implements AssignmentStrategy {

    @Override public String key() { return "load"; }
    @Override public String displayName() { return "Load-balanced"; }
    @Override public String description() { return "Lowest workload utilisation first; skill match breaks ties."; }

    @Override
    public List<RankedTechnician> rank(Incident incident, List<Candidate> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::loadRatio)
                        .thenComparing(c -> !c.skilledFor(incident)))
                .map(c -> new RankedTechnician(c, SkillBasedStrategy.round(1 - c.loadRatio()),
                        "Utilisation %.0f%%%s".formatted(c.loadRatio() * 100, c.skilledFor(incident) ? ", skilled" : "")))
                .toList();
    }
}
