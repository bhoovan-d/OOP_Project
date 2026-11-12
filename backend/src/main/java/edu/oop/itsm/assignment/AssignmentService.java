package edu.oop.itsm.assignment;

import edu.oop.itsm.domain.Incident;
import edu.oop.itsm.domain.IncidentStatus;
import edu.oop.itsm.domain.Technician;
import edu.oop.itsm.exception.TechnicianUnavailableException;
import edu.oop.itsm.exception.ValidationException;
import edu.oop.itsm.exception.WorkloadExceededException;
import edu.oop.itsm.repository.IncidentRepository;
import edu.oop.itsm.repository.TechnicianRepository;
import edu.oop.itsm.service.SettingsService;
import org.springframework.stereotype.Service;

import java.util.*;

/** Context of the Strategy pattern: gathers candidates once, then delegates the decision to the active strategy. */
@Service
public class AssignmentService {

    private final Map<String, AssignmentStrategy> strategies = new LinkedHashMap<>();
    private final SettingsService settings;
    private final TechnicianRepository technicians;
    private final IncidentRepository incidents;

    public AssignmentService(List<AssignmentStrategy> all, SettingsService settings,
                             TechnicianRepository technicians, IncidentRepository incidents) {
        all.forEach(s -> strategies.put(s.key(), s));
        this.settings = settings;
        this.technicians = technicians;
        this.incidents = incidents;
    }

    public Collection<AssignmentStrategy> strategies() { return strategies.values(); }

    public AssignmentStrategy active() {
        return strategies.getOrDefault(settings.get(SettingsService.ASSIGNMENT_STRATEGY), strategies.get("smart"));
    }

    public void setActive(String key) {
        if (!strategies.containsKey(key)) throw new ValidationException("Unknown assignment strategy: " + key + ". Options: " + strategies.keySet());
        settings.set(SettingsService.ASSIGNMENT_STRATEGY, key);
    }

    /** Picks a technician with the active strategy; falls back to load-balancing if the strategy finds nobody. */
    public Optional<Technician> autoSelect(Incident incident) {
        List<Candidate> pool = candidates(incident);
        if (pool.isEmpty()) return Optional.empty();
        AssignmentStrategy strategy = active();
        List<RankedTechnician> ranked = strategy.rank(incident, pool);
        if (ranked.isEmpty()) ranked = strategies.get("load").rank(incident, pool);
        Optional<Technician> chosen = ranked.stream().findFirst().map(r -> r.candidate().technician());
        chosen.ifPresent(strategy::onAssigned);
        return chosen;
    }

    /** Smart recommendation list (always the weighted model, whatever strategy is active). */
    public List<RankedTechnician> recommend(Incident incident, int limit) {
        return strategies.get("smart").rank(incident, candidates(incident)).stream().limit(limit).toList();
    }

    public void checkCanTake(Technician t) {
        if (!t.isActive() || !t.isAvailable())
            throw new TechnicianUnavailableException(t.getFullName() + " is not available for new incidents");
        long load = currentLoads().getOrDefault(t.getId(), 0L);
        if (load >= t.getMaxWorkload())
            throw new WorkloadExceededException("%s already has %d/%d active incidents".formatted(t.getFullName(), load, t.getMaxWorkload()));
    }

    public Map<Long, Long> currentLoads() {
        Map<Long, Long> loads = new HashMap<>();
        for (Object[] row : incidents.countLoadByTechnician(IncidentStatus.workloadStates())) loads.put((Long) row[0], (Long) row[1]);
        return loads;
    }

    /** 3 grouped queries regardless of team size. */
    List<Candidate> candidates(Incident incident) {
        Map<Long, Long> loads = currentLoads();
        Map<Long, Long> byService = toMap(incidents.countResolvedByTechnicianForService(incident.getService().getId()));
        Map<Long, Long> byCategory = incident.getCategory() == null ? Map.of()
                : toMap(incidents.countResolvedByTechnicianForCategory(incident.getCategory().getId()));
        Long currentAssignee = incident.getAssignee() == null ? null : incident.getAssignee().getId();

        List<Candidate> out = new ArrayList<>();
        for (Technician t : technicians.findByActiveTrueOrderByFullName()) {
            long load = loads.getOrDefault(t.getId(), 0L);
            if (!t.canAccept((int) load) || t.getId().equals(currentAssignee)) continue;
            out.add(new Candidate(t, load, byService.getOrDefault(t.getId(), 0L), byCategory.getOrDefault(t.getId(), 0L)));
        }
        return out;
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] r : rows) m.put((Long) r[0], (Long) r[1]);
        return m;
    }
}
