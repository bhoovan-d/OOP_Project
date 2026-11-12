package edu.oop.itsm.domain;

import edu.oop.itsm.exception.InvalidStateTransitionException;
import edu.oop.itsm.exception.MissingSlaConfigurationException;
import edu.oop.itsm.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** Business rules of the Incident aggregate and its State-pattern lifecycle. Pure unit tests, no Spring. */
class IncidentLifecycleTest {

    static final Instant T0 = Instant.parse("2026-11-12T10:00:00Z");
    Incident incident;
    Technician tech;
    SlaPolicy gold;

    @BeforeEach
    void setUp() {
        User reporter = new Employee("Rohan", "rohan@x.io", "hash");
        ItService payment = new ItService("Payment", "", Criticality.MISSION_CRITICAL);
        incident = new Incident("Payment app down", "Users cannot pay", reporter, payment, Impact.EXTENSIVE, Urgency.CRITICAL, 500, T0);
        tech = new Technician("Neha", "neha@x.io", "hash");
        gold = new SlaPolicy("Gold", "", targets());
        incident.applyPriority(Priority.P1, 80, "test");
        incident.applySla(gold); // P1: respond 5 min, resolve 30 min
    }

    static Map<Priority, SlaTarget> targets() {
        Map<Priority, SlaTarget> m = new EnumMap<>(Priority.class);
        m.put(Priority.P1, new SlaTarget(5, 30));
        m.put(Priority.P2, new SlaTarget(15, 120));
        m.put(Priority.P3, new SlaTarget(60, 480));
        m.put(Priority.P4, new SlaTarget(240, 1440));
        return m;
    }

    @Test
    @DisplayName("TC01 Business rule: NEW -> CLOSED is rejected (no skipping the lifecycle)")
    void cannotCloseNewIncident() {
        assertThat(IncidentStatus.NEW.canTransitionTo(IncidentStatus.CLOSED)).isFalse();
        assertThatThrownBy(() -> incident.close("x", T0)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("TC02 Normal operation: full lifecycle NEW -> ... -> CLOSED")
    void happyPath() {
        Category app = new Category("Application", "", java.util.List.of("payment"));
        incident.classify(app, true, 0.9, T0.plusSeconds(10));
        incident.assignTo(tech, T0.plusSeconds(20));
        incident.acknowledge(T0.plusSeconds(60));
        incident.startWork(T0.plusSeconds(90));
        incident.resolve("Rolled back release", "Bad deploy", T0.plusSeconds(600));
        incident.verify(T0.plusSeconds(700));
        incident.close("done", T0.plusSeconds(800));
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(incident.getSlaStatus()).isEqualTo(SlaStatus.MET);
        assertThat(incident.isBreached()).isFalse();
    }

    @Test
    @DisplayName("TC03 Terminal states allow no further transitions")
    void terminalStates() {
        assertThat(IncidentStatus.CLOSED.allowedNext()).isEmpty();
        assertThat(IncidentStatus.CANCELLED.allowedNext()).isEmpty();
        assertThat(IncidentStatus.openStates()).doesNotContain(IncidentStatus.RESOLVED, IncidentStatus.CLOSED);
    }

    @Test
    @DisplayName("TC04 Invalid input: resolving without resolution text is rejected")
    void resolveNeedsText() {
        toInProgress();
        assertThatThrownBy(() -> incident.resolve("  ", null, T0.plusSeconds(300))).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("TC05 Boundary: acknowledging 1 second after the response deadline records a response breach")
    void lateAcknowledgementBreaches() {
        incident.classify(new Category("A", "", null), false, null, T0);
        incident.assignTo(tech, T0);
        incident.acknowledge(T0.plus(Duration.ofMinutes(5)).plusSeconds(1));
        assertThat(incident.isResponseBreached()).isTrue();
    }

    @Test
    @DisplayName("TC06 SLA: AT_RISK once 75% of the window passes, BREACHED after the deadline")
    void slaEvaluation() {
        toInProgress();
        assertThat(incident.evaluateSla(T0.plus(Duration.ofMinutes(10)), 0.75)).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(incident.evaluateSla(T0.plus(Duration.ofMinutes(23)), 0.75)).isEqualTo(SlaStatus.AT_RISK);
        assertThat(incident.evaluateSla(T0.plus(Duration.ofMinutes(31)), 0.75)).isEqualTo(SlaStatus.BREACHED);
        assertThat(incident.isResolutionBreached()).isTrue();
    }

    @Test
    @DisplayName("TC07 SLA clock pauses while waiting on the reporter and the deadline moves by the paused time")
    void pauseExtendsDeadline() {
        toInProgress();
        Instant due = incident.getResolutionDueAt();
        incident.requestInfo(T0.plus(Duration.ofMinutes(5)));
        incident.provideInfo(T0.plus(Duration.ofMinutes(25)));
        assertThat(incident.getResolutionDueAt()).isEqualTo(due.plus(Duration.ofMinutes(20)));
    }

    @Test
    @DisplayName("TC08 Feedback only after resolution, rating must be 1-5")
    void feedbackRules() {
        assertThatThrownBy(() -> incident.recordFeedback(5, "x")).isInstanceOf(InvalidStateTransitionException.class);
        toInProgress();
        incident.resolve("fixed", null, T0.plusSeconds(400));
        assertThatThrownBy(() -> incident.recordFeedback(6, "x")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> incident.recordFeedback(0, "x")).isInstanceOf(ValidationException.class);
        incident.recordFeedback(4, "good");
        assertThat(incident.getFeedbackRating()).isEqualTo(4);
    }

    @Test
    @DisplayName("TC09 Missing SLA configuration is detected")
    void missingSla() {
        Map<Priority, SlaTarget> partial = new EnumMap<>(Priority.class);
        partial.put(Priority.P1, new SlaTarget(5, 30));
        assertThatThrownBy(() -> new SlaPolicy("Broken", "", partial)).isInstanceOf(MissingSlaConfigurationException.class);
        assertThatThrownBy(() -> new SlaTarget(60, 30)).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("TC10 Escalation chain stops at IT manager")
    void escalationChain() {
        assertThat(incident.escalate(T0)).isEqualTo(EscalationLevel.TEAM_LEAD);
        assertThat(incident.escalate(T0)).isEqualTo(EscalationLevel.SYSTEM_ADMIN);
        assertThat(incident.escalate(T0)).isEqualTo(EscalationLevel.IT_MANAGER);
        assertThat(incident.escalate(T0)).isEqualTo(EscalationLevel.IT_MANAGER);
    }

    @Test
    @DisplayName("TC11 Invalid input: incident without title or with zero affected users is rejected")
    void constructorValidation() {
        User u = new Employee("a", "a@x.io", "h");
        ItService s = new ItService("S", "", Criticality.LOW);
        assertThatThrownBy(() -> new Incident(" ", "d", u, s, Impact.LOW, Urgency.LOW, 1, T0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new Incident("t", "d", u, s, Impact.LOW, Urgency.LOW, 0, T0)).isInstanceOf(ValidationException.class);
    }

    private void toInProgress() {
        incident.classify(new Category("A", "", null), false, null, T0);
        incident.assignTo(tech, T0);
        incident.acknowledge(T0.plusSeconds(30));
        incident.startWork(T0.plusSeconds(60));
    }
}
