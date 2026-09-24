package org.ewsfi.signalpolicy.topology;

/**
 * Builds the Kafka Streams topology holding per-entity signal-episode state and evaluating signal
 * policies against {@code ews.derived.feature}, producing {@code ews.derived.signal} events. First
 * targets per docs/architecture/06-gap-analysis-and-implementation-roadmap.md Section 7: P01
 * DPD_EMERGED, P02 DPD_WORSENING, P03 REPEATED_PAYMENT_RETURN
 * (docs/architecture/02a-priority-signal-contracts.md). No operators wired yet.
 */
public class SignalPolicyTopology {
}
