package org.ewsfi.signalpolicy.policy;

/**
 * Hard-coded rule-method (R) policy for P11 CURRENT_RATIO_DERIORATION
 * (docs/architecture/02a-priority-signal-contracts.md: "Short-term balance-sheet liquidity
 * materially weakens; interpretation is sector/portfolio contextual."), mirroring
 * {@link SignalPolicyLoader}'s simplification of loading a single policy from Java constants rather
 * than the {@code signal_policy} table.
 *
 * <p>Fires on a materially decreasing {@code current_ratio} between consecutive observations for a
 * counterparty -- the mirror image of {@link LeverageDeteriorationSignalPolicyLoader}'s
 * materially-increasing check (lower current ratio means weaker short-term liquidity, unlike
 * leverage where higher is worse), using the same relative-threshold reasoning: current ratios also
 * vary widely by sector, so a proportional decrease is a more portable "materially weakens" signal
 * than an absolute one.
 */
public final class CurrentRatioDeteriorationSignalPolicyLoader {

    public static final String POLICY_ID = "POL-CURRENT-RATIO-DERIORATION-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SIGNAL_TYPE = "CURRENT_RATIO_DERIORATION";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";

    /** A >=20% relative decrease in current_ratio is treated as material. */
    public static final double MATERIAL_RELATIVE_DECREASE = 0.20;

    private CurrentRatioDeteriorationSignalPolicyLoader() {
    }

    public static boolean evaluate(double previousRatio, double currentRatio) {
        if (previousRatio <= 0) {
            // No meaningful prior baseline to compare a relative decrease against.
            return false;
        }
        double relativeDecrease = (previousRatio - currentRatio) / previousRatio;
        return relativeDecrease >= MATERIAL_RELATIVE_DECREASE;
    }
}
