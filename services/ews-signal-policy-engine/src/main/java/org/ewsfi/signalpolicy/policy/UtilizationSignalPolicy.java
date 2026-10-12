package org.ewsfi.signalpolicy.policy;

import java.math.BigDecimal;

/**
 * Rule-method policies for P05 {@code UTILIZATION_HIGH} and P06 {@code UTILIZATION_SPIKE}.
 * Thresholds are institution placeholders: high means utilization at or above 0.90 of sanctioned
 * capacity, and a spike is an increase of at least 0.15 versus the previous ratio for that
 * facility. They are not a drawing-power or regulatory limit rule.
 */
public final class UtilizationSignalPolicy {

    public static final String HIGH = "UTILIZATION_HIGH";
    public static final String SPIKE = "UTILIZATION_SPIKE";
    public static final String HIGH_POLICY_ID = "POL-UTILIZATION-HIGH-CORP-001";
    public static final String SPIKE_POLICY_ID = "POL-UTILIZATION-SPIKE-CORP-001";
    public static final String POLICY_VERSION = "1.0";
    public static final String SEMANTIC_SCOPE = "GLOBAL_CORE";
    public static final String LAST_RATIO_STORE = "utilization-last-ratio";
    public static final BigDecimal HIGH_THRESHOLD = new BigDecimal("0.90");
    public static final BigDecimal SPIKE_DELTA = new BigDecimal("0.15");

    private UtilizationSignalPolicy() {}

    public static boolean crossesIntoHigh(BigDecimal previous, BigDecimal current) {
        boolean nowHigh = current.compareTo(HIGH_THRESHOLD) >= 0;
        boolean wasHigh = previous != null && previous.compareTo(HIGH_THRESHOLD) >= 0;
        return nowHigh && !wasHigh;
    }

    public static boolean isSpike(BigDecimal previous, BigDecimal current) {
        return previous != null && current.subtract(previous).compareTo(SPIKE_DELTA) >= 0;
    }

    public static String policyId(String signalType) {
        return SPIKE.equals(signalType) ? SPIKE_POLICY_ID : HIGH_POLICY_ID;
    }
}
