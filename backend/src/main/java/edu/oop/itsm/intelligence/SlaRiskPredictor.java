package edu.oop.itsm.intelligence;

import edu.oop.itsm.domain.Incident;
import edu.oop.itsm.domain.IncidentStatus;
import edu.oop.itsm.domain.SlaStatus;
import edu.oop.itsm.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Predictive SLA-violation detection. Projects the finish time of each open incident from
 * (a) how long incidents of the same category historically take to resolve, (b) the assignee's
 * current queue and (c) whether anyone has picked it up yet, then compares it with the deadline.
 */
@Service
@Transactional(readOnly = true)
public class SlaRiskPredictor {

    public record Risk(Incident incident, double risk, List<String> reasons) {}

    private final IncidentRepository incidents;
    private final Clock clock;

    public SlaRiskPredictor(IncidentRepository incidents, Clock clock) {
        this.incidents = incidents;
        this.clock = clock;
    }

    public List<Risk> predict(int limit) {
        Instant now = clock.instant();
        Map<Long, Double> avgMinutesByCategory = historicalResolutionMinutes(now);
        Map<Long, Long> load = new HashMap<>();
        for (Object[] row : incidents.countLoadByTechnician(IncidentStatus.workloadStates())) load.put((Long) row[0], (Long) row[1]);

        List<Risk> out = new ArrayList<>();
        for (Incident i : incidents.findByStatusIn(IncidentStatus.openStates())) {
            if (i.getResolutionDueAt() == null || i.isBreached() || i.getSlaStatus() == SlaStatus.PAUSED) continue;
            out.add(score(i, now, avgMinutesByCategory, load));
        }
        return out.stream().filter(r -> r.risk() >= 0.5)
                .sorted(Comparator.comparingDouble(Risk::risk).reversed()).limit(limit).toList();
    }

    Risk score(Incident i, Instant now, Map<Long, Double> avgMinutesByCategory, Map<Long, Long> load) {
        List<String> reasons = new ArrayList<>();
        double window = i.getResolutionTargetMinutes();
        double elapsed = i.resolutionElapsedFraction(now) * window;
        Double hist = i.getCategory() == null ? null : avgMinutesByCategory.get(i.getCategory().getId());
        double expected = hist != null ? hist : window * 0.6;
        if (hist != null && hist > window) reasons.add("%s incidents usually take %.0f min (target %.0f)".formatted(i.getCategory().getName(), hist, window));

        double remaining = Math.max(expected - elapsed, expected * 0.2);
        double factor = 1;
        if (i.getAssignee() == null) { factor += 0.5; reasons.add("No technician assigned"); }
        else {
            long q = load.getOrDefault(i.getAssignee().getId(), 1L);
            if (q > 2) { factor += 0.15 * (q - 2); reasons.add("Assignee has %d active incidents".formatted(q)); }
        }
        if (i.getAcknowledgedAt() == null && i.responseElapsedFraction(now) > 0.5) { factor += 0.2; reasons.add("Not yet acknowledged"); }
        double elapsedPct = elapsed / window;
        if (elapsedPct > 0.5) reasons.add("%.0f%% of resolution window used".formatted(elapsedPct * 100));

        double risk = Math.min(1, (elapsed + remaining * factor) / window);
        return new Risk(i, Math.round(risk * 100) / 100.0, reasons);
    }

    private Map<Long, Double> historicalResolutionMinutes(Instant now) {
        Map<Long, double[]> acc = new HashMap<>();
        for (Object[] row : incidents.resolutionDurationsSince(now.minus(Duration.ofDays(90)))) {
            if (row[0] == null) continue;
            double minutes = Duration.between((Instant) row[1], (Instant) row[2]).toMinutes();
            double[] a = acc.computeIfAbsent((Long) row[0], k -> new double[2]);
            a[0] += minutes;
            a[1]++;
        }
        Map<Long, Double> avg = new HashMap<>();
        acc.forEach((k, a) -> avg.put(k, a[0] / a[1]));
        return avg;
    }
}
