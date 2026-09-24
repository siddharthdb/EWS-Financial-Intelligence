package org.ewsfi.featureprocessor.topology;

/**
 * Builds the Kafka Streams topology computing Phase-1 features
 * (docs/architecture/02d-phase1-feature-catalogue.md), e.g. {@code current_dpd},
 * {@code returned_payment_count_30d}, {@code wc_utilization_ratio}, from
 * {@code ews.canonical.*} topics into {@code ews.derived.feature}
 * (docs/architecture/03c-topic-and-partition-strategy.md Section 2). No operators wired yet.
 */
public class FeatureProcessorTopology {
}
