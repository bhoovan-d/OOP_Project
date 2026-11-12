package edu.oop.itsm.assignment;

import edu.oop.itsm.domain.Incident;
import edu.oop.itsm.domain.Technician;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Rotates through skilled technicians (everyone, if nobody is skilled) in a fixed order. */
@Component
public class RoundRobinStrategy implements AssignmentStrategy {

    // ponytail: rotation pointer is per JVM instance; move to the settings table if running multiple nodes
    private final AtomicLong lastAssignedId = new AtomicLong(0);

    @Override public String key() { return "round-robin"; }
    @Override public String displayName() { return "Round-robin"; }
    @Override public String description() { return "Takes turns among skilled technicians for fair distribution."; }

    @Override
    public List<RankedTechnician> rank(Incident incident, List<Candidate> candidates) {
        List<Candidate> pool = candidates.stream().filter(c -> c.skilledFor(incident)).toList();
        if (pool.isEmpty()) pool = candidates;
        List<Candidate> ordered = new ArrayList<>(pool);
        ordered.sort(Comparator.comparing(c -> c.technician().getId()));

        long last = lastAssignedId.get();
        int start = 0;
        while (start < ordered.size() && ordered.get(start).technician().getId() <= last) start++;
        List<RankedTechnician> out = new ArrayList<>();
        for (int k = 0; k < ordered.size(); k++) {
            Candidate c = ordered.get((start + k) % ordered.size());
            out.add(new RankedTechnician(c, SkillBasedStrategy.round(1.0 - (double) k / ordered.size()), "Turn #" + (k + 1) + " in rotation"));
        }
        return out;
    }

    @Override
    public void onAssigned(Technician technician) {
        lastAssignedId.set(technician.getId());
    }
}
