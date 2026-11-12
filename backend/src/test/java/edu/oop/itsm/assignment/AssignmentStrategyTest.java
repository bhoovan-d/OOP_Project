package edu.oop.itsm.assignment;

import edu.oop.itsm.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Polymorphism check: four interchangeable strategies, same inputs, different (documented) choices. */
class AssignmentStrategyTest {

    final Category network = withId(new Category("Network", "", List.of("vpn")), 1L);
    final Category db = withId(new Category("Database", "", List.of("sql")), 2L);

    final Technician senior = tech(10L, "Senior", 9, Set.of(network));
    final Technician junior = tech(11L, "Junior", 1, Set.of(network));
    final Technician dba = tech(12L, "DBA", 6, Set.of(db));

    Incident incident(Priority p) {
        Incident i = new Incident("VPN down", "vpn drops", new Employee("e", "e@x.io", "h"),
                withId(new ItService("VPN", "", Criticality.HIGH), 5L), Impact.HIGH, Urgency.HIGH, 50, Instant.now());
        i.classify(network, true, 0.9, Instant.now());
        i.applyPriority(p, 30, "");
        return i;
    }

    @Test
    @DisplayName("TC16 Skill-based: only skilled technicians, least loaded first")
    void skillBased() {
        var ranked = new SkillBasedStrategy().rank(incident(Priority.P3),
                List.of(new Candidate(senior, 3, 0, 0), new Candidate(junior, 1, 0, 0), new Candidate(dba, 0, 0, 0)));
        assertThat(ranked).extracting(r -> r.candidate().technician().getFullName()).containsExactly("Junior", "Senior");
    }

    @Test
    @DisplayName("TC17 Load-balanced: lowest utilisation wins even without the skill")
    void loadBalanced() {
        var ranked = new LoadBalancedStrategy().rank(incident(Priority.P3),
                List.of(new Candidate(senior, 3, 0, 0), new Candidate(dba, 0, 0, 0)));
        assertThat(ranked.get(0).candidate().technician().getFullName()).isEqualTo("DBA");
    }

    @Test
    @DisplayName("TC18 Round-robin rotates through skilled technicians")
    void roundRobin() {
        RoundRobinStrategy rr = new RoundRobinStrategy();
        List<Candidate> c = List.of(new Candidate(senior, 0, 0, 0), new Candidate(junior, 0, 0, 0));
        Technician first = rr.rank(incident(Priority.P3), c).get(0).candidate().technician();
        rr.onAssigned(first);
        Technician second = rr.rank(incident(Priority.P3), c).get(0).candidate().technician();
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("TC19 Smart: for a P1 the experienced, skilled technician outranks an idle junior")
    void smart() {
        var ranked = new SmartStrategy().rank(incident(Priority.P1),
                List.of(new Candidate(junior, 0, 0, 0), new Candidate(senior, 2, 4, 3), new Candidate(dba, 0, 0, 0)));
        assertThat(ranked.get(0).candidate().technician().getFullName()).isEqualTo("Senior");
        assertThat(ranked.get(2).candidate().technician().getFullName()).isEqualTo("DBA");
    }

    @Test
    @DisplayName("TC20 Capacity rule: a technician at max workload or unavailable cannot accept work")
    void capacity() {
        senior.setMaxWorkload(3);
        assertThat(senior.canAccept(2)).isTrue();
        assertThat(senior.canAccept(3)).isFalse();
        senior.setAvailable(false);
        assertThat(senior.canAccept(0)).isFalse();
    }

    static Technician tech(Long id, String name, int exp, Set<Category> skills) {
        Technician t = withId(new Technician(name, name + "@x.io", "h"), id);
        t.setExperienceYears(exp);
        t.setSkills(skills);
        return t;
    }

    static <T> T withId(T o, Long id) {
        ReflectionTestUtils.setField(o, "id", id);
        return o;
    }
}
