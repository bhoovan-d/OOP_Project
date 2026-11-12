package edu.oop.itsm.intelligence;

import edu.oop.itsm.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntelligenceTest {

    final PriorityEngine engine = new PriorityEngine();

    @Test
    @DisplayName("TC12 Priority: organisation-wide outage of the payment service is P1, a single-user nuisance is P4")
    void priorityBands() {
        assertThat(engine.assess(Impact.EXTENSIVE, Urgency.CRITICAL, Criticality.MISSION_CRITICAL, 2000).priority()).isEqualTo(Priority.P1);
        assertThat(engine.assess(Impact.LOW, Urgency.LOW, Criticality.LOW, 1).priority()).isEqualTo(Priority.P4);
        assertThat(engine.assess(Impact.MEDIUM, Urgency.HIGH, Criticality.HIGH, 5).priority()).isEqualTo(Priority.P2); // 18 = P2 boundary
        assertThat(engine.assess(Impact.MEDIUM, Urgency.MEDIUM, Criticality.LOW, 5).priority()).isEqualTo(Priority.P4); // 4
    }

    @Test
    @DisplayName("TC13 Priority boundary: the affected-user multiplier lifts a score across a band")
    void userMultiplier() {
        // base 2*2*4 = 16 -> P3; with >1000 users 16*1.75 = 28 -> P2
        assertThat(engine.assess(Impact.MEDIUM, Urgency.MEDIUM, Criticality.CRITICAL, 10).priority()).isEqualTo(Priority.P3);
        assertThat(engine.assess(Impact.MEDIUM, Urgency.MEDIUM, Criticality.CRITICAL, 5000).priority()).isEqualTo(Priority.P2);
    }

    @Test
    @DisplayName("TC14 Duplicate detection: differently-worded payment reports are similar, unrelated ones are not")
    void textSimilarity() {
        var a = TextAnalyzer.incidentVector("Payment application unavailable", "Users are unable to complete transactions");
        var b = TextAnalyzer.incidentVector("Unable to make payment", "Checkout fails");
        var c = TextAnalyzer.incidentVector("Printer not working", "2nd floor printer offline");
        assertThat(TextAnalyzer.cosine(a, b)).isGreaterThan(0.5);
        assertThat(TextAnalyzer.cosine(a, c)).isLessThan(0.2);
    }

    @Test
    @DisplayName("TC15 Auto-categorisation picks the category whose keywords match the description")
    void classifier() {
        Category network = cat(1L, "Network", "vpn", "wifi", "network");
        Category auth = cat(2L, "Authentication", "login", "sso", "password");
        List<Category> all = List.of(network, auth);
        CategoryClassifier cc = new CategoryClassifier();
        assertThat(cc.suggest("VPN keeps dropping", "remote network disconnects", null, all).get(0).name()).isEqualTo("Network");
        assertThat(cc.suggest("Cannot sign in via SSO", "login page errors", null, all).get(0).name()).isEqualTo("Authentication");
    }

    static Category cat(Long id, String name, String... kw) {
        Category c = new Category(name, "", List.of(kw));
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }
}
