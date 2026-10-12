package org.ewsfi.signalpolicy.policy;

/**
 * Hard-coded rule-method (R) policy for P03 REPEATED_PAYMENT_RETURN
 * (docs/architecture/02a-priority-signal-contracts.md), matching the shape of the jurisdiction-
 * neutral policy example in docs/architecture/04-signal-taxonomy.md Section 18
 * ({@code POL-PAYMENT-RETURN-CORP-001}). A single policy, loaded from Java constants rather than
 * from the {@code signal_policy} table, is a deliberate simplification. DPD policies live in
 * {@link DpdSignalPolicy}. Loading arbitrary persisted policies
 * (schemas/signals/signal-policy-v1.schema.json) is still future work.
 */
public final class SignalPolicyLoader {

    public static final String POLICY_ID = "POL-PAYMENT-RETURN-CORP-001";
    public static final String POLICY_VERSION = "2.0";
    public static final String SIGNAL_TYPE = "REPEATED_PAYMENT_RETURN";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";
    public static final long THRESHOLD = 3L;

    private SignalPolicyLoader() {
    }

    /** Per the policy example's {@code condition}: {@code returned_payment_count_30d >= threshold}. */
    public static boolean evaluate(long returnedPaymentCount30d) {
        return returnedPaymentCount30d >= THRESHOLD;
    }
}
