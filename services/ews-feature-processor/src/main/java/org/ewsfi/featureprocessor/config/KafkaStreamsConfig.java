package org.ewsfi.featureprocessor.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.kstream.KStream;
import org.ewsfi.featureprocessor.topology.DpdFeatureTopology;
import org.ewsfi.featureprocessor.topology.FeatureProcessorTopology;
import org.ewsfi.featureprocessor.topology.CurrentRatioFeatureTopology;
import org.ewsfi.featureprocessor.topology.FilingDelayFeatureTopology;
import org.ewsfi.featureprocessor.topology.LeverageRatioFeatureTopology;
import org.ewsfi.featureprocessor.topology.OperatingCashFlowFeatureTopology;
import org.ewsfi.featureprocessor.topology.OperatingIncomeFeatureTopology;
import org.ewsfi.featureprocessor.topology.ReceivableDaysFeatureTopology;
import org.ewsfi.featureprocessor.topology.MaxDpdFeatureTopology;
import org.ewsfi.featureprocessor.topology.UtilizationDeltaFeatureTopology;
import org.ewsfi.featureprocessor.topology.UtilizationFeatureTopology;
import org.ewsfi.featureprocessor.topology.WcAvailableHeadroomFeatureTopology;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;

/**
 * Wires {@link FeatureProcessorTopology} into a running Kafka Streams application via Spring
 * Kafka's {@code @EnableKafkaStreams} support, per ADR-004 (Kafka Streams as the Phase-1 default
 * for stateful event processing).
 */
@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    /**
     * Roadmap item 3.6 (production hardening): without an explicit
     * {@link StreamsUncaughtExceptionHandler}, Kafka Streams' default behavior on an uncaught
     * exception in a stream thread (e.g. {@code MaxDpdFeatureTopology}'s aggregator throwing a
     * {@code ClassCastException} if a well-formed {@code obligation.dpd.changed} envelope somehow
     * carries a non-numeric {@code currentDpd}) is to let that thread die -- with only one stream
     * thread configured (the default), that kills the whole Kafka Streams client, halting every
     * topology in this application, not just the one that hit the bad record. {@code REPLACE_THREAD}
     * is Kafka's own documented mitigation: the failed thread is replaced and processing continues,
     * rather than one edge-case record taking down feature computation platform-wide.
     */
    @Bean
    public StreamsBuilderFactoryBeanConfigurer streamsUncaughtExceptionHandlerConfigurer() {
        return factoryBean ->
                factoryBean.setStreamsUncaughtExceptionHandler(
                        throwable -> StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD);
    }

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kStreamsConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "ews-feature-processor");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaStreamsConfiguration(props);
    }

    @Bean
    public KStream<String, String> featureProcessorStream(
            StreamsBuilder streamsBuilder, FeatureProcessorTopology topology) {
        // Spring Kafka builds the actual Topology/KafkaStreams instance from the shared
        // StreamsBuilder bean after all @Bean methods that touch it have run.
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> dpdFeatureStream(
            StreamsBuilder streamsBuilder, DpdFeatureTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> utilizationFeatureStream(
            StreamsBuilder streamsBuilder, UtilizationFeatureTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> maxDpdFeatureStream(
            StreamsBuilder streamsBuilder, MaxDpdFeatureTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> utilizationDeltaFeatureStream(
            StreamsBuilder streamsBuilder, UtilizationDeltaFeatureTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> filingDelayFeatureStream(
            StreamsBuilder streamsBuilder, FilingDelayFeatureTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> leverageRatioFeatureStream(
            StreamsBuilder streamsBuilder, LeverageRatioFeatureTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> currentRatioFeatureStream(
            StreamsBuilder streamsBuilder, CurrentRatioFeatureTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> wcAvailableHeadroomFeatureStream(
            StreamsBuilder streamsBuilder, WcAvailableHeadroomFeatureTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> operatingIncomeFeatureStream(
            StreamsBuilder streamsBuilder, OperatingIncomeFeatureTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> operatingCashFlowFeatureStream(
            StreamsBuilder streamsBuilder, OperatingCashFlowFeatureTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> receivableDaysFeatureStream(
            StreamsBuilder streamsBuilder, ReceivableDaysFeatureTopology topology) {
        return topology.build(streamsBuilder);
    }
}
