package org.ewsfi.signalpolicy.policy;

/**
 * Hard-coded rule-method (R) policy for P02 DPD_WORSENING
 * (docs/architecture/02a-priority-signal-contracts.md: "Repayment delinquency materially
 * increases. Features: DPD velocity, rolling max, cure/relapse. Do not emit unchanged daily
 * duplicates."), mirroring {@link SignalPolicyLoader}'s simplification of loading a single policy
 * from Java constants rather than the {@code signal_policy} table.
 *
 * <p>A true "DPD velocity" would be a rate over time; this policy instead fires on a materially
 * increasing {@code max_dpd_30d} between consecutive observations for a facility -- a simpler,
 * documented interim proxy that still captures the contract's core intent ("materially
 * increases") and naturally satisfies "do not emit unchanged daily duplicates" (an unchanged or
 * decreased max never meets the threshold).
 */
public final class DpdWorseningSignalPolicyLoader {

    public static final String POLICY_ID = "POL-DPD-WORSENING-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SIGNAL_TYPE = "DPD_WORSENING";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";
    public static final int MATERIAL_INCREASE_THRESHOLD_DAYS = 10;

    private DpdWorseningSignalPolicyLoader() {
    }

    public static boolean evaluate(int previousMax, int currentMax) {
        return currentMax - previousMax >= MATERIAL_INCREASE_THRESHOLD_DAYS;
    }
}
