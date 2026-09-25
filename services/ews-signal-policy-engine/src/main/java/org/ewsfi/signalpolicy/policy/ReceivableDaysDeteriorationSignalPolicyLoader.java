package org.ewsfi.signalpolicy.policy;

/**
 * Hard-coded rule-method (R) policy for P15 RECEIVABLE_DAYS_DERIORATION
 * (docs/architecture/02a-priority-signal-contracts.md: "Collection cycle materially lengthens."),
 * mirroring {@link SignalPolicyLoader}'s simplification of loading a single policy from Java
 * constants rather than the {@code signal_policy} table.
 *
 * <p>Fires on a materially increasing {@code receivable_days} between consecutive observations for
 * a counterparty -- the same relative-threshold pattern
 * {@link LeverageDeteriorationSignalPolicyLoader} uses (higher receivable_days is worse, like
 * leverage, unlike current_ratio where lower is worse).
 */
public final class ReceivableDaysDeteriorationSignalPolicyLoader {

    public static final String POLICY_ID = "POL-RECEIVABLE-DAYS-DERIORATION-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SIGNAL_TYPE = "RECEIVABLE_DAYS_DERIORATION";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";

    /** A >=20% relative increase in receivable_days is treated as material. */
    public static final double MATERIAL_RELATIVE_INCREASE = 0.20;

    private ReceivableDaysDeteriorationSignalPolicyLoader() {
    }

    public static boolean evaluate(double previousDays, double currentDays) {
        if (previousDays <= 0) {
            return false;
        }
        double relativeIncrease = (currentDays - previousDays) / previousDays;
        return relativeIncrease >= MATERIAL_RELATIVE_INCREASE;
    }
}
