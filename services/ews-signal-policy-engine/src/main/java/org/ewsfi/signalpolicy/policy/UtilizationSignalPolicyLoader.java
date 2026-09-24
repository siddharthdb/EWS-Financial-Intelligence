package org.ewsfi.signalpolicy.policy;

/**
 * Hard-coded rule-method (R) policy for P05 UTILIZATION_HIGH
 * (docs/architecture/02a-priority-signal-contracts.md: "Exposure is persistently close to
 * available committed/approved capacity"), mirroring {@link SignalPolicyLoader}'s simplification
 * of loading a single policy from Java constants rather than the {@code signal_policy} table.
 *
 * <p>"Persistently" in the contract's own wording implies a sustained condition, not a single
 * threshold crossing; this policy implements the simpler single-observation threshold, the same
 * simplification {@code SignalPolicyTopology} already documents for REPEATED_PAYMENT_RETURN
 * (emits on every window where the threshold is met, no episode/sustain tracking). P06
 * UTILIZATION_SPIKE (a velocity/trend signal against a rolling baseline) is a separate, harder
 * signal, deferred per roadmap item 1.14's scoping decision.
 */
public final class UtilizationSignalPolicyLoader {

    public static final String POLICY_ID = "POL-UTILIZATION-HIGH-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SIGNAL_TYPE = "UTILIZATION_HIGH";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";
    public static final double THRESHOLD = 0.9;

    private UtilizationSignalPolicyLoader() {
    }

    public static boolean evaluate(double wcUtilizationRatio) {
        return wcUtilizationRatio >= THRESHOLD;
    }
}
