package org.ewsfi.signalpolicy.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.kstream.KStream;
import org.ewsfi.signalpolicy.topology.DpdSignalTopology;
import org.ewsfi.signalpolicy.topology.CurrentRatioDeteriorationSignalTopology;
import org.ewsfi.signalpolicy.topology.DpdWorseningSignalTopology;
import org.ewsfi.signalpolicy.topology.LeverageDeteriorationSignalTopology;
import org.ewsfi.signalpolicy.topology.LimitExcessRecurringSignalTopology;
import org.ewsfi.signalpolicy.topology.RequiredMonitoringDelaySignalTopology;
import org.ewsfi.signalpolicy.topology.SignalPolicyTopology;
import org.ewsfi.signalpolicy.topology.UtilizationSignalTopology;
import org.ewsfi.signalpolicy.topology.UtilizationSpikeSignalTopology;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;

/**
 * Wires {@link SignalPolicyTopology} into a running Kafka Streams application via Spring Kafka's
 * {@code @EnableKafkaStreams} support, per ADR-004.
 */
@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Roadmap item 3.6 (production hardening): without this, Kafka Streams' default behavior on an
     * uncaught exception in a stream thread is to let that thread die, which (with the default one
     * stream thread) kills the whole client -- halting every signal topology in this application,
     * not just the one that hit a bad record. {@code REPLACE_THREAD} is Kafka's own documented
     * mitigation. Mirrors {@code ews-feature-processor}'s identical configurer.
     */
    @Bean
    public StreamsBuilderFactoryBeanConfigurer streamsUncaughtExceptionHandlerConfigurer() {
        return factoryBean ->
                factoryBean.setStreamsUncaughtExceptionHandler(
                        throwable -> StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD);
    }

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kStreamsConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "ews-signal-policy-engine");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaStreamsConfiguration(props);
    }

    @Bean
    public KStream<String, String> signalPolicyStream(
            StreamsBuilder streamsBuilder, SignalPolicyTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> dpdSignalStream(
            StreamsBuilder streamsBuilder, DpdSignalTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> utilizationSignalStream(
            StreamsBuilder streamsBuilder, UtilizationSignalTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> dpdWorseningSignalStream(
            StreamsBuilder streamsBuilder, DpdWorseningSignalTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> utilizationSpikeSignalStream(
            StreamsBuilder streamsBuilder, UtilizationSpikeSignalTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> requiredMonitoringDelaySignalStream(
            StreamsBuilder streamsBuilder, RequiredMonitoringDelaySignalTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> leverageDeteriorationSignalStream(
            StreamsBuilder streamsBuilder, LeverageDeteriorationSignalTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> currentRatioDeteriorationSignalStream(
            StreamsBuilder streamsBuilder, CurrentRatioDeteriorationSignalTopology topology) {
        return topology.build(streamsBuilder);
    }

    @Bean
    public KStream<String, String> limitExcessRecurringSignalStream(
            StreamsBuilder streamsBuilder, LimitExcessRecurringSignalTopology topology) {
        return topology.build(streamsBuilder);
    }
}
