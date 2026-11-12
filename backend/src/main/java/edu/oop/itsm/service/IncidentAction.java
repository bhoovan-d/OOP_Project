package edu.oop.itsm.service;

import edu.oop.itsm.domain.Incident;
import edu.oop.itsm.domain.IncidentStatus;

import java.util.EnumSet;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static edu.oop.itsm.domain.IncidentStatus.*;

/**
 * Every user-triggerable operation with two rules: WHO may do it (role / relationship to the incident)
 * and WHEN (incident state). The same table drives authorisation in the API and which buttons the UI shows,
 * so the two can never disagree.
 */
public enum IncidentAction {
    COMMENT((r, i) -> true, i -> i.getStatus() != CLOSED && i.getStatus() != CANCELLED),
    NOTE((r, i) -> r.staff(), Incident::isOpen),
    EDIT((r, i) -> r.admin() || r.reporter() || r.assignee(), Incident::isOpen),
    REASSESS((r, i) -> r.admin() || r.assignee(), Incident::isOpen),
    ASSIGN((r, i) -> r.admin(), i -> i.getStatus().canTransitionTo(ASSIGNED)),
    TAKE((r, i) -> r.technician(), i -> i.getStatus() == CLASSIFIED),
    ACKNOWLEDGE((r, i) -> r.assignee() || r.admin(), i -> i.getStatus() == ASSIGNED),
    START((r, i) -> r.assignee() || r.admin(), i -> i.getStatus() == ACKNOWLEDGED),
    REQUEST_INFO((r, i) -> r.assignee() || r.admin(), i -> i.getStatus() == IN_PROGRESS),
    RESOLVE((r, i) -> r.assignee() || r.admin(), i -> i.getStatus() == IN_PROGRESS),
    PROVIDE_INFO((r, i) -> r.reporter() || r.admin(), i -> i.getStatus() == AWAITING_INFO),
    VERIFY((r, i) -> r.reporter() || r.admin(), i -> i.getStatus() == RESOLVED),
    REOPEN((r, i) -> r.reporter() || r.admin(), i -> i.getStatus() == RESOLVED),
    CLOSE((r, i) -> r.admin() || ((r.reporter() || r.assignee()) && i.getStatus() == VERIFIED),
            i -> i.getStatus() == VERIFIED || i.getStatus() == RESOLVED),
    FEEDBACK((r, i) -> r.reporter(),
            i -> EnumSet.of(RESOLVED, VERIFIED, CLOSED).contains(i.getStatus()) && i.getFeedbackRating() == null),
    CANCEL((r, i) -> r.admin() || (r.reporter() && EnumSet.of(NEW, CLASSIFIED, ASSIGNED).contains(i.getStatus())),
            i -> i.getStatus().canTransitionTo(CANCELLED)),
    MERGE((r, i) -> r.staff(), i -> i.getStatus().canTransitionTo(CANCELLED));

    /** The caller's relationship to one incident. */
    public record Relation(boolean admin, boolean technician, boolean reporter, boolean assignee) {
        public boolean staff() { return admin || technician; }
    }

    private final BiPredicate<Relation, Incident> who;
    private final Predicate<Incident> when;

    IncidentAction(BiPredicate<Relation, Incident> who, Predicate<Incident> when) {
        this.who = who;
        this.when = when;
    }

    public boolean permits(Relation r, Incident i) { return who.test(r, i); }

    public boolean applicable(Incident i) { return when.test(i); }

}
