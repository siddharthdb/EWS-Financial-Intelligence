package org.ewsfi.signalpolicy.policy;

/**
 * Hard-coded rule-method (R) policy for P16 INVENTORY_DAYS_DERIORATION
 * (docs/architecture/02a-priority-signal-contracts.md: "Inventory holding period materially
 * increases."), mirroring {@link SignalPolicyLoader}'s simplification of loading a single policy
 * from Java constants rather than the {@code signal_policy} table.
 *
 * <p>Fires on a materially increasing {@code inventory_days} between consecutive observations for a
 * counterparty -- the same relative-threshold pattern
 * {@link ReceivableDaysDeteriorationSignalPolicyLoader} uses (higher inventory_days is worse, like
 * receivable_days and leverage).
 */
public final class InventoryDaysDeteriorationSignalPolicyLoader {

    public static final String POLICY_ID = "POL-INVENTORY-DAYS-DERIORATION-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SIGNAL_TYPE = "INVENTORY_DAYS_DERIORATION";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";

    /** A >=20% relative increase in inventory_days is treated as material. */
    public static final double MATERIAL_RELATIVE_INCREASE = 0.20;

    private InventoryDaysDeteriorationSignalPolicyLoader() {
    }

    public static boolean evaluate(double previousDays, double currentDays) {
        if (previousDays <= 0) {
            return false;
        }
        double relativeIncrease = (currentDays - previousDays) / previousDays;
        return relativeIncrease >= MATERIAL_RELATIVE_INCREASE;
    }
}
