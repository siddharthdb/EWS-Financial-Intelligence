package org.ewsfi.signalpolicy.policy;

/**
 * Hard-coded rule-method (R) policy for P01 DPD_EMERGED
 * (docs/architecture/02a-priority-signal-contracts.md), mirroring {@link SignalPolicyLoader}'s
 * simplification of loading a single policy from Java constants rather than the {@code
 * signal_policy} table -- see that class's Javadoc for why.
 *
 * <p>P02 DPD_WORSENING (a velocity/trend signal, method S) is deferred to roadmap item 1.18, not
 * implemented here.
 */
public final class DpdSignalPolicyLoader {

    public static final String POLICY_ID = "POL-DPD-EMERGED-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SIGNAL_TYPE = "DPD_EMERGED";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";

    private DpdSignalPolicyLoader() {
    }

    /**
     * DPD_EMERGED fires exactly on a genuine 0-or-unknown to positive DPD transition -- a facility
     * that was current (or had no prior observation) newly falling into delinquency. A transition
     * between two already-positive DPD values, or a 0-to-0 no-op, must not fire this policy (that
     * distinction is DPD_WORSENING's job, per docs/architecture/02a-priority-signal-contracts.md P02).
     */
    public static boolean evaluate(Integer previousDpd, int currentDpd) {
        boolean wasCurrent = previousDpd == null || previousDpd == 0;
        return wasCurrent && currentDpd > 0;
    }
}
