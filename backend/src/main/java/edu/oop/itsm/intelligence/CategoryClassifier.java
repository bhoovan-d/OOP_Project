package edu.oop.itsm.intelligence;

import edu.oop.itsm.domain.Category;
import edu.oop.itsm.domain.ItService;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Suggests a category from free text. Each category owns a keyword list (admin-editable),
 * so new categories are learned by configuration, not code. Title hits weigh double;
 * the affected service's default category adds a prior.
 */
@Component
public class CategoryClassifier {

    public record Suggestion(Long categoryId, String name, double confidence, List<String> matchedKeywords) {}

    public List<Suggestion> suggest(String title, String description, ItService service, List<Category> categories) {
        Set<String> titleTokens = TextAnalyzer.termFrequencies(title).keySet();
        Set<String> bodyTokens = TextAnalyzer.termFrequencies(description).keySet();
        Long defaultCategoryId = service != null && service.getDefaultCategory() != null ? service.getDefaultCategory().getId() : null;

        record Scored(Category c, double score, List<String> hits) {}
        List<Scored> scored = new ArrayList<>();
        double total = 0;
        for (Category c : categories) {
            double score = 0;
            List<String> hits = new ArrayList<>();
            for (String kw : c.getKeywords()) {
                String token = TextAnalyzer.normalize(kw);
                if (token == null) continue;
                boolean inTitle = titleTokens.contains(token), inBody = bodyTokens.contains(token);
                if (inTitle || inBody) {
                    score += (inTitle ? 2 : 0) + (inBody ? 1 : 0);
                    hits.add(kw);
                }
            }
            if (c.getId().equals(defaultCategoryId)) score += 1.5;
            if (score > 0) {
                scored.add(new Scored(c, score, hits));
                total += score;
            }
        }
        double denominator = total + 1; // +1 damps confidence when evidence is thin
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(3)
                .map(s -> new Suggestion(s.c().getId(), s.c().getName(), Math.round(s.score() / denominator * 100) / 100.0, s.hits()))
                .toList();
    }
}
