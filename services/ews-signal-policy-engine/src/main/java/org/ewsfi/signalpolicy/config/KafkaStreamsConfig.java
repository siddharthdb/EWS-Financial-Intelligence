package org.ewsfi.signalpolicy.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.ewsfi.signalpolicy.topology.DpdSignalTopology;
import org.ewsfi.signalpolicy.topology.SignalPolicyTopology;
import org.ewsfi.signalpolicy.topology.UtilizationSignalTopology;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

/**
 * Wires {@link SignalPolicyTopology} into a running Kafka Streams application via Spring Kafka's
 * {@code @EnableKafkaStreams} support, per ADR-004.
 */
@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

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
}
