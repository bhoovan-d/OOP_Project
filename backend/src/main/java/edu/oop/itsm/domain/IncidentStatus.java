package edu.oop.itsm.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * State pattern (enum flavour): every lifecycle state encapsulates which states it may move to
 * and how it behaves (is it open? does the SLA clock run? does it count as technician workload?).
 * Adding a state means adding one constant here; the Incident aggregate never hard-codes transitions.
 */
public enum IncidentStatus {
    NEW {
        @Override public Set<IncidentStatus> allowedNext() { return EnumSet.of(CLASSIFIED, CANCELLED); }
    },
    CLASSIFIED {
        @Override public Set<IncidentStatus> allowedNext() { return EnumSet.of(ASSIGNED, CANCELLED); }
    },
    ASSIGNED {
        @Override public Set<IncidentStatus> allowedNext() { return EnumSet.of(ACKNOWLEDGED, ASSIGNED, CANCELLED); }
        @Override public boolean countsAsWorkload() { return true; }
    },
    ACKNOWLEDGED {
        @Override public Set<IncidentStatus> allowedNext() { return EnumSet.of(IN_PROGRESS, ASSIGNED, CANCELLED); }
        @Override public boolean countsAsWorkload() { return true; }
    },
    IN_PROGRESS {
        @Override public Set<IncidentStatus> allowedNext() { return EnumSet.of(AWAITING_INFO, RESOLVED, ASSIGNED, CANCELLED); }
        @Override public boolean countsAsWorkload() { return true; }
    },
    AWAITING_INFO {
        @Override public Set<IncidentStatus> allowedNext() { return EnumSet.of(IN_PROGRESS, CANCELLED); }
        @Override public boolean countsAsWorkload() { return true; }
        @Override public boolean slaClockRunning() { return false; }
    },
    RESOLVED {
        @Override public Set<IncidentStatus> allowedNext() { return EnumSet.of(VERIFIED, IN_PROGRESS, CLOSED); }
        @Override public boolean isOpen() { return false; }
        @Override public boolean slaClockRunning() { return false; }
    },
    VERIFIED {
        @Override public Set<IncidentStatus> allowedNext() { return EnumSet.of(CLOSED); }
        @Override public boolean isOpen() { return false; }
        @Override public boolean slaClockRunning() { return false; }
    },
    CLOSED {
        @Override public Set<IncidentStatus> allowedNext() { return EnumSet.noneOf(IncidentStatus.class); }
        @Override public boolean isOpen() { return false; }
        @Override public boolean slaClockRunning() { return false; }
    },
    CANCELLED {
        @Override public Set<IncidentStatus> allowedNext() { return EnumSet.noneOf(IncidentStatus.class); }
        @Override public boolean isOpen() { return false; }
        @Override public boolean slaClockRunning() { return false; }
    };

    public abstract Set<IncidentStatus> allowedNext();

    public boolean canTransitionTo(IncidentStatus target) { return allowedNext().contains(target); }

    /** Open = still needs work from IT. */
    public boolean isOpen() { return true; }

    /** Whether resolution-SLA time is currently ticking. */
    public boolean slaClockRunning() { return true; }

    /** Whether an assigned incident in this state occupies a technician slot. */
    public boolean countsAsWorkload() { return false; }

    public static Set<IncidentStatus> openStates() {
        EnumSet<IncidentStatus> s = EnumSet.noneOf(IncidentStatus.class);
        for (IncidentStatus st : values()) if (st.isOpen()) s.add(st);
        return s;
    }

    public static Set<IncidentStatus> workloadStates() {
        EnumSet<IncidentStatus> s = EnumSet.noneOf(IncidentStatus.class);
        for (IncidentStatus st : values()) if (st.countsAsWorkload()) s.add(st);
        return s;
    }
}
