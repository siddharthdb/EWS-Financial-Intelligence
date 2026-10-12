package org.ewsfi.signalpolicy.policy;

/**
 * Rule-method policies for P01 {@code DPD_EMERGED} and P02 {@code DPD_WORSENING}
 * (docs/architecture/02a-priority-signal-contracts.md).
 *
 * <p>Institution bands, not regulatory classifications: 0, 1–29, 30–59, 60–89, 90+. Emerging is a
 * crossing out of the zero band. Worsening is a crossing into a higher band, so an unchanged daily
 * DPD — and a one-day increase that stays inside the same band — does not open another proposed
 * signal. These bands are calibration placeholders, not SMA/NPA or IFRS 9 stages.
 */
public final class DpdSignalPolicy {

    public static final String EMERGED = "DPD_EMERGED";
    public static final String WORSENING = "DPD_WORSENING";
    public static final String EMERGED_POLICY_ID = "POL-DPD-EMERGED-CORP-001";
    public static final String WORSENING_POLICY_ID = "POL-DPD-WORSENING-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";
    public static final String LAST_DPD_STORE = "dpd-last-value";

    private DpdSignalPolicy() {
    }

    /**
     * @param previous last known DPD for the facility; null is treated as not-yet-overdue
     * @return the signal type to propose, or null when no new signal is warranted
     */
    public static String classify(Integer previous, int current) {
        if (current < 0) {
            return null;
        }
        int prior = previous == null ? 0 : previous;
        if (prior <= 0 && current >= 1) {
            return EMERGED;
        }
        if (prior >= 1 && band(current) > band(prior)) {
            return WORSENING;
        }
        return null;
    }

    public static String policyId(String signalType) {
        return WORSENING.equals(signalType) ? WORSENING_POLICY_ID : EMERGED_POLICY_ID;
    }

    public static String severity(int currentDpd) {
        if (currentDpd >= 90) {
            return "CRITICAL";
        }
        if (currentDpd >= 60) {
            return "HIGH";
        }
        if (currentDpd >= 30) {
            return "MEDIUM";
        }
        return "LOW";
    }

    public static String materiality(int currentDpd) {
        if (currentDpd >= 90) {
            return "CRITICAL";
        }
        if (currentDpd >= 30) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    static int band(int dpd) {
        if (dpd <= 0) {
            return 0;
        }
        if (dpd < 30) {
            return 1;
        }
        if (dpd < 60) {
            return 2;
        }
        if (dpd < 90) {
            return 3;
        }
        return 4;
    }
}
