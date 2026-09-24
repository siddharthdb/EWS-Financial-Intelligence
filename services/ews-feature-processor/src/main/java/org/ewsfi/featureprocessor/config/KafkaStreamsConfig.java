package org.ewsfi.featureprocessor.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.ewsfi.featureprocessor.topology.DpdFeatureTopology;
import org.ewsfi.featureprocessor.topology.FeatureProcessorTopology;
import org.ewsfi.featureprocessor.topology.UtilizationFeatureTopology;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

/**
 * Wires {@link FeatureProcessorTopology} into a running Kafka Streams application via Spring
 * Kafka's {@code @EnableKafkaStreams} support, per ADR-004 (Kafka Streams as the Phase-1 default
 * for stateful event processing).
 */
@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

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
}
