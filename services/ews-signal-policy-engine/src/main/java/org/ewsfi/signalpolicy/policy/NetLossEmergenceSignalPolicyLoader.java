package org.ewsfi.signalpolicy.policy;

/**
 * Hard-coded rule-method (R) policy for NET_LOSS_EMERGENCE
 * (docs/architecture/04-signal-taxonomy.md Section 4: "income statement", method R) -- a
 * taxonomy-defined signal outside the curated P01-P34 priority contract list, part of roadmap item
 * 2.3's remaining scope. Mirrors {@link SignalPolicyLoader}'s simplification of loading a single
 * policy from Java constants rather than the {@code signal_policy} table.
 *
 * <p>Fires on a genuine profit-to-loss transition between two <em>known</em> consecutive
 * {@code net_income} observations for a counterparty: the previous observation was non-negative
 * (profit or breakeven) and the current one is negative (a loss). Deliberately does not treat a
 * missing/unknown previous observation as an implicit "was profitable" baseline (unlike
 * {@link DpdSignalPolicyLoader}'s DPD_EMERGED, which treats an unknown previous DPD as equivalent to
 * zero) -- "emergence" specifically means a fresh transition, and firing on a company's very first
 * tracked observation being a loss would claim knowledge of a transition this platform hasn't
 * actually observed. This is a documented, more conservative interim policy choice.
 */
public final class NetLossEmergenceSignalPolicyLoader {

    public static final String POLICY_ID = "POL-NET-LOSS-EMERGENCE-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SIGNAL_TYPE = "NET_LOSS_EMERGENCE";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";

    private NetLossEmergenceSignalPolicyLoader() {
    }

    public static boolean evaluate(double previousNetIncome, double currentNetIncome) {
        return previousNetIncome >= 0 && currentNetIncome < 0;
    }
}
