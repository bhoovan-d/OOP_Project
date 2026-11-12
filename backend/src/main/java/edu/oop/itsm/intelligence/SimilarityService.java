package edu.oop.itsm.intelligence;

import edu.oop.itsm.domain.Incident;
import edu.oop.itsm.domain.IncidentStatus;
import edu.oop.itsm.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Duplicate detection + suggested resolutions, both built on {@link TextAnalyzer} cosine similarity.
 * ponytail: scans the latest 300 open / 500 resolved incidents in memory; move to a full-text or vector index past ~100k rows.
 */
@Service
@Transactional(readOnly = true)
public class SimilarityService {

    public static final double DUPLICATE_THRESHOLD = 0.60;
    private static final double SIMILAR_THRESHOLD = 0.40;
    private static final double RESOLUTION_THRESHOLD = 0.25;
    private static final Duration DUPLICATE_WINDOW = Duration.ofHours(72);

    public record Match(Incident incident, double score) {}

    private final IncidentRepository incidents;
    private final Clock clock;

    public SimilarityService(IncidentRepository incidents, Clock clock) {
        this.incidents = incidents;
        this.clock = clock;
    }

    /** Open incidents from the last 72h that look like the same underlying problem. */
    public List<Match> findSimilarOpen(String title, String description, Long serviceId, Long excludeId, int limit) {
        Map<String, Integer> v = TextAnalyzer.incidentVector(title, description);
        return incidents.findTop300ByStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
                        IncidentStatus.openStates(), clock.instant().minus(DUPLICATE_WINDOW)).stream()
                .filter(i -> !i.getId().equals(excludeId))
                .map(i -> {
                    double text = TextAnalyzer.cosine(v, TextAnalyzer.incidentVector(i.getTitle(), i.getDescription()));
                    boolean sameService = Objects.equals(i.getService().getId(), serviceId);
                    // same service alone is not evidence; it only boosts textual similarity
                    double score = text < 0.15 ? 0 : 0.75 * text + (sameService ? 0.25 : 0);
                    return new Match(i, Math.round(score * 100) / 100.0);
                })
                .filter(m -> m.score() >= SIMILAR_THRESHOLD)
                .sorted(Comparator.comparingDouble(Match::score).reversed())
                .limit(limit)
                .toList();
    }

    /** Resolved incidents similar to this one; their resolutions are offered as suggestions. */
    public List<Match> findSimilarResolved(String title, String description, Long categoryId, Long excludeId, int limit) {
        Map<String, Integer> v = TextAnalyzer.incidentVector(title, description);
        java.util.Set<String> seen = new java.util.HashSet<>();
        return incidents.findTop500ByResolutionIsNotNullOrderByResolvedAtDesc().stream()
                .filter(i -> !i.getId().equals(excludeId))
                .map(i -> {
                    double text = TextAnalyzer.cosine(v, TextAnalyzer.incidentVector(i.getTitle(), i.getDescription()));
                    boolean sameCategory = i.getCategory() != null && i.getCategory().getId().equals(categoryId);
                    return new Match(i, Math.round(Math.min(1, text + (sameCategory && text > 0 ? 0.1 : 0)) * 100) / 100.0);
                })
                .filter(m -> m.score() >= RESOLUTION_THRESHOLD)
                .sorted(Comparator.comparingDouble(Match::score).reversed())
                .filter(m -> seen.add(m.incident().getResolution().toLowerCase())) // one suggestion per distinct fix
                .limit(limit)
                .toList();
    }
}
