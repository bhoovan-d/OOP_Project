package edu.oop.itsm.intelligence;

import edu.oop.itsm.domain.Criticality;
import edu.oop.itsm.domain.Impact;
import edu.oop.itsm.domain.Priority;
import edu.oop.itsm.domain.Urgency;
import org.springframework.stereotype.Component;

/**
 * Weighted, rule-based priority algorithm.
 * <pre>
 *   base  = Impact(1-4) x Urgency(1-4) x Criticality(1-5)            -> 1 .. 80
 *   users = 1.00 (<=10)  1.25 (<=100)  1.50 (<=1000)  1.75 (>1000)  -> blast-radius multiplier
 *   score = base x users                                             -> 1 .. 140
 *   P1 >= 36,  P2 >= 18,  P3 >= 8,  else P4
 * </pre>
 * Rationale: the product keeps "low on any axis" incidents low (a single-user issue on a critical
 * service is not P1), while the user multiplier lets wide-spread degradation climb a band.
 * Hard override: EXTENSIVE impact on a MISSION_CRITICAL service is always P1.
 */
@Component
public class PriorityEngine {

    public record Assessment(Priority priority, double score, String explanation) {}

    public Assessment assess(Impact impact, Urgency urgency, Criticality criticality, int affectedUsers) {
        int base = impact.weight() * urgency.weight() * criticality.weight();
        double multiplier = affectedUsers <= 10 ? 1.0 : affectedUsers <= 100 ? 1.25 : affectedUsers <= 1000 ? 1.5 : 1.75;
        double score = Math.round(base * multiplier * 100) / 100.0;

        Priority p = score >= 36 ? Priority.P1 : score >= 18 ? Priority.P2 : score >= 8 ? Priority.P3 : Priority.P4;
        boolean override = impact == Impact.EXTENSIVE && criticality == Criticality.MISSION_CRITICAL;
        if (override) p = Priority.P1;

        String why = "Impact %s(%d) x Urgency %s(%d) x Criticality %s(%d) = %d; x%.2f for %d affected user(s) = %.1f -> %s%s"
                .formatted(impact, impact.weight(), urgency, urgency.weight(), criticality, criticality.weight(), base,
                        multiplier, affectedUsers, score, p, override ? " (override: organisation-wide outage of mission-critical service)" : "");
        return new Assessment(p, score, why);
    }
}
