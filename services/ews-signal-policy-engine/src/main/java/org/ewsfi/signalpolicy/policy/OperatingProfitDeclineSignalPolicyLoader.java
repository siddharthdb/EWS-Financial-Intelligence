package org.ewsfi.signalpolicy.policy;

/**
 * Hard-coded rule-method (R) policy for P13 OPERATING_PROFIT_MATERIAL_DECLINE
 * (docs/architecture/02a-priority-signal-contracts.md: "Operating performance materially
 * deteriorates against history, plan or peers."), mirroring {@link SignalPolicyLoader}'s
 * simplification of loading a single policy from Java constants rather than the
 * {@code signal_policy} table.
 *
 * <p>Implements the "against history" case only (see {@code OperatingIncomeFeatureTopology}'s
 * javadoc for why "against plan" is not implemented). Fires on a materially decreasing
 * {@code operating_income} between consecutive observations for a counterparty -- the same
 * relative-threshold pattern {@link CurrentRatioDeteriorationSignalPolicyLoader} uses. Requires the
 * *previous* observation to be positive: a relative decline computed against a previous loss (or
 * zero) is not a meaningful percentage, mirroring {@link LeverageDeteriorationSignalPolicyLoader}'s
 * identical non-positive-baseline guard.
 */
public final class OperatingProfitDeclineSignalPolicyLoader {

    public static final String POLICY_ID = "POL-OPERATING-PROFIT-DECLINE-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SIGNAL_TYPE = "OPERATING_PROFIT_MATERIAL_DECLINE";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";

    /** A >=20% relative decrease in operating_income is treated as material. */
    public static final double MATERIAL_RELATIVE_DECREASE = 0.20;

    private OperatingProfitDeclineSignalPolicyLoader() {
    }

    public static boolean evaluate(double previousOperatingIncome, double currentOperatingIncome) {
        if (previousOperatingIncome <= 0) {
            return false;
        }
        double relativeDecrease =
                (previousOperatingIncome - currentOperatingIncome) / previousOperatingIncome;
        return relativeDecrease >= MATERIAL_RELATIVE_DECREASE;
    }
}
