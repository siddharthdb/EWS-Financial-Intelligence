package org.ewsfi.signalpolicy.policy;

/**
 * Statistical-method (S) policy for P06 UTILIZATION_SPIKE
 * (docs/architecture/02a-priority-signal-contracts.md: "Utilization rises materially relative to
 * recent baseline"), the platform's first signal driven by a statistically-computed baseline
 * comparison rather than a raw-value threshold or transition -- distinct from
 * {@link UtilizationSignalPolicyLoader} (P05 UTILIZATION_HIGH, a raw-value threshold) and
 * {@link DpdWorseningSignalPolicyLoader} (a consecutive-value magnitude delta). Deferred from
 * roadmap item 1.14, closed out here as part of item 2.3.
 *
 * <p>Mirrors the project's established simplification of a single hard-coded policy rather than
 * loading from the {@code signal_policy} table (see {@link SignalPolicyLoader}'s Javadoc).
 */
public final class UtilizationSpikeSignalPolicyLoader {

    public static final String POLICY_ID = "POL-UTILIZATION-SPIKE-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SIGNAL_TYPE = "UTILIZATION_SPIKE";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";
    public static final double MATERIAL_DELTA_THRESHOLD = 0.15;

    private UtilizationSpikeSignalPolicyLoader() {
    }

    public static boolean evaluate(double wcUtilizationDelta30d) {
        return wcUtilizationDelta30d >= MATERIAL_DELTA_THRESHOLD;
    }
}
