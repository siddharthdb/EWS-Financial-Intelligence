package org.ewsfi.signalpolicy.policy;

/**
 * Hard-coded rule-method (R) policy for P18 REQUIRED_MONITORING_INFORMATION_DELAY
 * (docs/architecture/02a-priority-signal-contracts.md: "Required monitoring information was not
 * received by governed due date"), mirroring {@link SignalPolicyLoader}'s simplification of
 * loading a single policy from Java constants rather than the {@code signal_policy} table.
 *
 * <p>Applied here specifically to SEC periodic filings (10-K/10-Q), via
 * {@code financial_statement_filing_delay_days}. SEC's own regulatory deadlines vary by filer
 * category (10-K: 60/75/90 days for large-accelerated/accelerated/non-accelerated filers; 10-Q:
 * 40/40/45 days) and by form type -- this policy does not track filer category or distinguish form
 * type, using a single conservative threshold (90 days) that is the maximum across every category
 * and form this platform currently ingests, a documented simplification. A filing flagged by this
 * threshold is genuinely late under every SEC filer category; a filing that isn't flagged may still
 * be late for a stricter category not modeled here.
 */
public final class RequiredMonitoringDelaySignalPolicyLoader {

    public static final String POLICY_ID = "POL-REQUIRED-MONITORING-DELAY-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SIGNAL_TYPE = "REQUIRED_MONITORING_INFORMATION_DELAY";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";
    public static final int THRESHOLD_DAYS = 90;

    private RequiredMonitoringDelaySignalPolicyLoader() {
    }

    public static boolean evaluate(int filingDelayDays) {
        return filingDelayDays > THRESHOLD_DAYS;
    }
}
