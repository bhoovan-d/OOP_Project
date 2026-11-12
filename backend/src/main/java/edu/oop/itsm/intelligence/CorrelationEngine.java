package edu.oop.itsm.intelligence;

import edu.oop.itsm.domain.Incident;
import edu.oop.itsm.domain.IncidentStatus;
import edu.oop.itsm.domain.ItService;
import edu.oop.itsm.domain.Priority;
import edu.oop.itsm.repository.IncidentRepository;
import edu.oop.itsm.repository.ItServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Incident correlation: groups open incidents by the service / underlying problem they share and
 * derives per-service health. Three or more fresh incidents on one service within 30 minutes
 * are declared a major incident.
 */
@Service
@Transactional(readOnly = true)
public class CorrelationEngine {

    public static final int MAJOR_INCIDENT_THRESHOLD = 3;
    public static final Duration MAJOR_INCIDENT_WINDOW = Duration.ofMinutes(30);

    public enum Health { HEALTHY, DEGRADED, OUTAGE }

    public record ServiceHealth(Long serviceId, String service, String criticality, long open, long recent, long critical, Health health) {}

    public record Cluster(Long rootId, String rootNumber, String title, String service, List<String> members) {}

    private final IncidentRepository incidents;
    private final ItServiceRepository services;
    private final Clock clock;

    public CorrelationEngine(IncidentRepository incidents, ItServiceRepository services, Clock clock) {
        this.incidents = incidents;
        this.services = services;
        this.clock = clock;
    }

    /** True exactly when this new incident pushes its service over the threshold (so we alert once). */
    public boolean crossesMajorIncidentThreshold(Long serviceId) {
        long recent = incidents.countByServiceIdAndStatusInAndCreatedAtAfter(
                serviceId, IncidentStatus.openStates(), clock.instant().minus(MAJOR_INCIDENT_WINDOW));
        return recent == MAJOR_INCIDENT_THRESHOLD;
    }

    public List<ServiceHealth> serviceHealth() {
        Instant recentCut = clock.instant().minus(Duration.ofMinutes(60));
        Map<Long, List<Incident>> open = new HashMap<>();
        for (Incident i : incidents.findByStatusIn(IncidentStatus.openStates()))
            open.computeIfAbsent(i.getService().getId(), k -> new ArrayList<>()).add(i);

        List<ServiceHealth> out = new ArrayList<>();
        for (ItService s : services.findByActiveTrueOrderByName()) {
            List<Incident> list = open.getOrDefault(s.getId(), List.of());
            long recent = list.stream().filter(i -> i.getCreatedAt().isAfter(recentCut)).count();
            long critical = list.stream().filter(i -> i.getPriority() == Priority.P1).count();
            Health h = recent >= MAJOR_INCIDENT_THRESHOLD || critical > 0 ? Health.OUTAGE
                    : list.size() >= 2 || recent >= 2 ? Health.DEGRADED : Health.HEALTHY;
            out.add(new ServiceHealth(s.getId(), s.getName(), s.getCriticality().name(), list.size(), recent, critical, h));
        }
        out.sort(Comparator.comparing((ServiceHealth x) -> x.health().ordinal()).reversed().thenComparing(ServiceHealth::open, Comparator.reverseOrder()));
        return out;
    }

    /** Groups open incidents linked as duplicates under their root incident. */
    public List<Cluster> clusters() {
        Map<Incident, List<String>> groups = new LinkedHashMap<>();
        for (Incident i : incidents.findByStatusIn(IncidentStatus.openStates())) {
            if (i.getDuplicateOf() == null) continue;
            Incident root = i.getDuplicateOf();
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(i.getNumber());
        }
        List<Cluster> out = new ArrayList<>();
        groups.forEach((root, members) -> out.add(new Cluster(root.getId(), root.getNumber(), root.getTitle(), root.getService().getName(), members)));
        out.sort(Comparator.comparingInt((Cluster c) -> c.members().size()).reversed());
        return out;
    }
}
