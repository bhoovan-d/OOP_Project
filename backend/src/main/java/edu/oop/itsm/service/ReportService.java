package edu.oop.itsm.service;

import edu.oop.itsm.domain.*;
import edu.oop.itsm.event.Actor;
import edu.oop.itsm.intelligence.CorrelationEngine;
import edu.oop.itsm.intelligence.SlaRiskPredictor;
import edu.oop.itsm.repository.IncidentRepository;
import edu.oop.itsm.repository.TechnicianRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

/** Operational analytics. Aggregation happens in SQL (GROUP BY) wherever possible. */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public record Overview(long total, long open, long criticalOpen, long resolved, long breached, long atRisk, long unassigned,
                           double mttrMinutes, double slaCompliancePct, double avgRating, long ratings) {}

    public record Count(String label, long value) {}

    public record ServiceRow(String service, long total, long open, long breached) {}

    public record TechnicianRow(Long id, String name, long active, int max, double utilisation, long resolved,
                                Double avgRating, long breaches, boolean available, List<String> skills) {}

    public record TrendPoint(String day, long created, long resolved, long breached) {}

    public record RiskRow(Long id, String number, String title, Priority priority, String assignee, double risk,
                          List<String> reasons, Instant resolutionDueAt) {}

    public record Dashboard(Overview overview, List<Count> byStatus, List<Count> byPriority, List<Count> byCategory,
                            List<ServiceRow> byService, List<TechnicianRow> technicians, List<TrendPoint> trend,
                            List<Count> mttrByPriority, List<CorrelationEngine.ServiceHealth> serviceHealth,
                            List<CorrelationEngine.Cluster> clusters, List<RiskRow> slaRisk) {}

    public record MyStats(long active, long resolvedThisWeek, Double avgRating, long breaches, int maxWorkload, List<Count> byStatus) {}

    private final IncidentRepository incidents;
    private final TechnicianRepository technicians;
    private final CorrelationEngine correlation;
    private final SlaRiskPredictor riskPredictor;
    private final Clock clock;

    public ReportService(IncidentRepository incidents, TechnicianRepository technicians, CorrelationEngine correlation,
                         SlaRiskPredictor riskPredictor, Clock clock) {
        this.incidents = incidents;
        this.technicians = technicians;
        this.correlation = correlation;
        this.riskPredictor = riskPredictor;
        this.clock = clock;
    }

    public Dashboard dashboard(int days) {
        Instant now = clock.instant();
        Set<IncidentStatus> open = IncidentStatus.openStates();
        Set<IncidentStatus> done = EnumSet.of(IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED);

        // ---- trend + MTTR from one scan of the window ----
        ZoneId zone = ZoneOffset.UTC;
        LocalDate firstDay = LocalDate.ofInstant(now, zone).minusDays(days - 1L);
        Map<LocalDate, long[]> perDay = new TreeMap<>();
        for (int d = 0; d < days; d++) perDay.put(firstDay.plusDays(d), new long[3]);
        Map<Priority, double[]> mttr = new EnumMap<>(Priority.class);
        double sumAll = 0; long nAll = 0, inSla = 0;
        for (Object[] r : incidents.timelineSince(firstDay.atStartOfDay(zone).toInstant())) {
            Instant created = (Instant) r[0], resolved = (Instant) r[1];
            Priority p = (Priority) r[2];
            boolean breached = Boolean.TRUE.equals(r[4]) || Boolean.TRUE.equals(r[5]);
            long[] c = perDay.get(LocalDate.ofInstant(created, zone));
            if (c != null) { c[0]++; if (breached) c[2]++; }
            if (resolved != null) {
                long[] rr = perDay.get(LocalDate.ofInstant(resolved, zone));
                if (rr != null) rr[1]++;
                double minutes = Duration.between(created, resolved).toMinutes();
                sumAll += minutes; nAll++;
                if (!breached) inSla++;
                if (p != null) { double[] m = mttr.computeIfAbsent(p, k -> new double[2]); m[0] += minutes; m[1]++; }
            }
        }
        List<TrendPoint> trend = new ArrayList<>();
        perDay.forEach((d, c) -> trend.add(new TrendPoint(d.toString(), c[0], c[1], c[2])));
        List<Count> mttrByPriority = new ArrayList<>();
        for (Priority p : Priority.values()) {
            double[] m = mttr.get(p);
            mttrByPriority.add(new Count(p.name(), m == null ? 0 : Math.round(m[0] / m[1])));
        }

        Object[] fb = incidents.feedbackSummary().get(0);
        Overview overview = new Overview(
                incidents.count(), incidents.countByStatusIn(open),
                incidents.countByStatusInAndPriority(open, Priority.P1),
                incidents.countByStatusIn(done), incidents.countBreached(), incidents.countAtRisk(open),
                incidents.count((root, q, cb) -> cb.and(root.get("status").in(open), cb.isNull(root.get("assignee")))),
                nAll == 0 ? 0 : Math.round(sumAll / nAll), nAll == 0 ? 100 : Math.round(inSla * 1000.0 / nAll) / 10.0,
                fb[0] == null ? 0 : Math.round(((Number) fb[0]).doubleValue() * 10) / 10.0, fb[1] == null ? 0 : ((Number) fb[1]).longValue());

        List<Count> byStatus = incidents.statusBreakdown().stream().map(r -> new Count(r[0].toString(), (Long) r[1])).toList();
        Map<Priority, Long> pr = new EnumMap<>(Priority.class);
        incidents.priorityBreakdown(open).forEach(r -> pr.put((Priority) r[0], (Long) r[1]));
        List<Count> byPriority = Arrays.stream(Priority.values()).map(p -> new Count(p.name(), pr.getOrDefault(p, 0L))).toList();
        List<Count> byCategory = incidents.categoryBreakdown().stream().map(r -> new Count((String) r[0], (Long) r[1])).toList();
        List<ServiceRow> byService = incidents.serviceBreakdown(open).stream()
                .map(r -> new ServiceRow((String) r[0], (Long) r[1], num(r[2]), num(r[3]))).toList();

        List<RiskRow> risk = riskPredictor.predict(8).stream().map(r -> new RiskRow(r.incident().getId(), r.incident().getNumber(),
                r.incident().getTitle(), r.incident().getPriority(),
                r.incident().getAssignee() == null ? null : r.incident().getAssignee().getFullName(),
                r.risk(), r.reasons(), r.incident().getResolutionDueAt())).toList();

        return new Dashboard(overview, byStatus, byPriority, byCategory, byService, technicianRows(), trend, mttrByPriority,
                correlation.serviceHealth(), correlation.clusters(), risk);
    }

    public List<TechnicianRow> technicianRows() {
        Map<Long, Long> load = new HashMap<>();
        incidents.countLoadByTechnician(IncidentStatus.workloadStates()).forEach(r -> load.put((Long) r[0], (Long) r[1]));
        Map<Long, Object[]> perf = new HashMap<>();
        incidents.technicianPerformance().forEach(r -> perf.put((Long) r[0], r));
        List<TechnicianRow> rows = new ArrayList<>();
        for (Technician t : technicians.findByActiveTrueOrderByFullName()) {
            long active = load.getOrDefault(t.getId(), 0L);
            Object[] p = perf.get(t.getId());
            rows.add(new TechnicianRow(t.getId(), t.getFullName(), active, t.getMaxWorkload(),
                    Math.round(active * 100.0 / t.getMaxWorkload()) / 100.0,
                    p == null ? 0 : (Long) p[1],
                    p == null || p[2] == null ? null : Math.round(((Number) p[2]).doubleValue() * 10) / 10.0,
                    p == null ? 0 : num(p[3]), t.isAvailable(),
                    t.getSkills().stream().map(Category::getName).sorted().toList()));
        }
        rows.sort(Comparator.comparingDouble(TechnicianRow::utilisation).reversed());
        return rows;
    }

    public MyStats myStats(Actor actor) {
        Technician t = technicians.findById(actor.id()).orElseThrow();
        long active = 0, resolvedWeek = 0, breaches = 0;
        double ratingSum = 0; int ratings = 0;
        Map<String, Long> byStatus = new TreeMap<>();
        Instant weekAgo = clock.instant().minus(Duration.ofDays(7));
        for (Incident i : incidents.findAll((root, q, cb) -> cb.equal(root.get("assignee").get("id"), actor.id()))) {
            if (i.getStatus().countsAsWorkload()) { active++; byStatus.merge(i.getStatus().name(), 1L, Long::sum); }
            if (i.getResolvedAt() != null && i.getResolvedAt().isAfter(weekAgo)) resolvedWeek++;
            if (i.isBreached()) breaches++;
            if (i.getFeedbackRating() != null) { ratingSum += i.getFeedbackRating(); ratings++; }
        }
        return new MyStats(active, resolvedWeek, ratings == 0 ? null : Math.round(ratingSum / ratings * 10) / 10.0, breaches,
                t.getMaxWorkload(), byStatus.entrySet().stream().map(e -> new Count(e.getKey(), e.getValue())).toList());
    }

    private static long num(Object o) { return o == null ? 0 : ((Number) o).longValue(); }
}
