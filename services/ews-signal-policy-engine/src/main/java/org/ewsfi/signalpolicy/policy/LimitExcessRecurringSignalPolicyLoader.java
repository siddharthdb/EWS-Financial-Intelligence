package org.ewsfi.signalpolicy.policy;

/**
 * Hard-coded rule-method (R) policy for P07 LIMIT_EXCESS_RECURRING
 * (docs/architecture/02a-priority-signal-contracts.md: "Exposure repeatedly/continuously exceeds
 * applicable approved/committed capacity. Regulatory out-of-order/default treatment remains
 * separate."), mirroring {@link SignalPolicyLoader}'s simplification of loading a single policy
 * from Java constants rather than the {@code signal_policy} table.
 *
 * <p>The catalogue's more precise companion feature for this contract,
 * {@code limit_excess_days_30d} ("number of days in rolling 30 days where eligible outstanding
 * exceeded applicable capacity"), is not implemented -- deriving distinct calendar days from an
 * irregular, event-driven observation stream (rather than daily snapshots) needs its own bucketing
 * logic, a materially harder problem than a windowed count/max. This policy instead uses {@code
 * wc_available_headroom} (already implemented) and requires the *two most recent consecutive*
 * observations for a facility to both be negative (outstanding exceeding the limit) before firing --
 * a single negative observation is a one-off excess, not yet "repeatedly/continuously" per the
 * contract's own wording, and requiring two consecutive negative observations is a genuine, if
 * coarser, interim proxy for recurrence.
 */
public final class LimitExcessRecurringSignalPolicyLoader {

    public static final String POLICY_ID = "POL-LIMIT-EXCESS-RECURRING-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SIGNAL_TYPE = "LIMIT_EXCESS_RECURRING";
    public static final String SEMANTIC_SCOPE = "GLOBAL_PRODUCT_SPECIFIC";

    private LimitExcessRecurringSignalPolicyLoader() {
    }

    public static boolean evaluate(double previousHeadroom, double currentHeadroom) {
        return previousHeadroom < 0 && currentHeadroom < 0;
    }
}
