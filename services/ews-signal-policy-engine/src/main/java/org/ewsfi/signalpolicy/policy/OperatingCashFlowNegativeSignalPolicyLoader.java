package org.ewsfi.signalpolicy.policy;

/**
 * Hard-coded rule-method (R) policy for P14 OPERATING_CASH_FLOW_NEGATIVE
 * (docs/architecture/02a-priority-signal-contracts.md: "Operating cash flow is negative for a
 * relevant period, interpreted with seasonality/business-model context."), mirroring
 * {@link SignalPolicyLoader}'s simplification of loading a single policy from Java constants rather
 * than the {@code signal_policy} table.
 *
 * <p>A direct threshold check on {@code operating_cash_flow} -- the simplest of the P10-P34
 * contracts implemented so far, since the contract's own condition ("negative for a relevant
 * period") requires no transition/trend state, unlike DPD/leverage/current-ratio deterioration. The
 * contract's own "interpreted with seasonality/business-model context" caveat is not implemented --
 * this policy fires on any single negative observation, a documented simplification (this platform
 * has no seasonality model).
 */
public final class OperatingCashFlowNegativeSignalPolicyLoader {

    public static final String POLICY_ID = "POL-OPERATING-CASH-FLOW-NEGATIVE-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SIGNAL_TYPE = "OPERATING_CASH_FLOW_NEGATIVE";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";

    private OperatingCashFlowNegativeSignalPolicyLoader() {
    }

    public static boolean evaluate(double operatingCashFlow) {
        return operatingCashFlow < 0;
    }
}
