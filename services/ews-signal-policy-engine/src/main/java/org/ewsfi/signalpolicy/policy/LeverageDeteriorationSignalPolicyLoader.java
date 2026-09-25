package org.ewsfi.signalpolicy.policy;

/**
 * Hard-coded rule-method (R) policy for P12 LEVERAGE_DERIORATION
 * (docs/architecture/02a-priority-signal-contracts.md: "Governed leverage materially worsens.
 * Implementations may use debt/EBITDA, debt/equity, TOL/ATNW or other approved measures."),
 * mirroring {@link SignalPolicyLoader}'s simplification of loading a single policy from Java
 * constants rather than the {@code signal_policy} table.
 *
 * <p>Fires on a materially increasing {@code total_liabilities_to_equity} between consecutive
 * observations for a counterparty -- the same "consecutive-observation delta" pattern
 * {@link DpdWorseningSignalPolicyLoader} uses for P02 DPD_WORSENING, applied here to a ratio rather
 * than an integer day count. A relative (percentage) threshold is used rather than an absolute one
 * (unlike DPD's day-count threshold) because leverage ratios vary by orders of magnitude across
 * filers -- an absolute delta of, say, 0.5 would be material for a lowly-levered filer and
 * meaningless for a highly-levered one, so "materially worsens" is defined proportionally to the
 * counterparty's own prior ratio.
 */
public final class LeverageDeteriorationSignalPolicyLoader {

    public static final String POLICY_ID = "POL-LEVERAGE-DERIORATION-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SIGNAL_TYPE = "LEVERAGE_DERIORATION";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";

    /** A >=20% relative increase in total_liabilities_to_equity is treated as material. */
    public static final double MATERIAL_RELATIVE_INCREASE = 0.20;

    private LeverageDeteriorationSignalPolicyLoader() {
    }

    public static boolean evaluate(double previousRatio, double currentRatio) {
        if (previousRatio <= 0) {
            // No meaningful prior baseline to compare a relative increase against.
            return false;
        }
        double relativeIncrease = (currentRatio - previousRatio) / previousRatio;
        return relativeIncrease >= MATERIAL_RELATIVE_INCREASE;
    }
}
