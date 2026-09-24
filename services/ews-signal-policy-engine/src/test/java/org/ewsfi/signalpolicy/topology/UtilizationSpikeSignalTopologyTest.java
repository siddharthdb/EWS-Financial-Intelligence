package org.ewsfi.signalpolicy.topology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.ewsfi.contracts.interim.JsonSignalDetected;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link UtilizationSpikeSignalTopology}'s policy evaluation in isolation using
 * {@link TopologyTestDriver} -- no broker required.
 */
class UtilizationSpikeSignalTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "utilization-spike-signal-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new UtilizationSpikeSignalTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        UtilizationSpikeSignalTopology.FEATURE_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        UtilizationSpikeSignalTopology.SIGNAL_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void firesWhenDeltaMeetsTheMaterialThreshold() {
        pipeDelta("fac-1", 0.20);

        List<String> outputs = outputTopic.readValuesToList();

        assertThat(outputs).hasSize(1);
        JsonSignalDetected signal = parse(outputs.get(0));
        assertThat(signal.getSignalType()).isEqualTo("UTILIZATION_SPIKE");
        assertThat(signal.getEntityType()).isEqualTo("FACILITY");
        assertThat(signal.getEntityId()).isEqualTo("fac-1");
        assertThat(signal.getStatus()).isEqualTo("PROPOSED");
    }

    @Test
    void doesNotFireBelowTheThreshold() {
        pipeDelta("fac-2", 0.05);

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void doesNotFireOnANegativeDelta() {
        pipeDelta("fac-3", -0.30);

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void ignoresUnrelatedFeatureNames() {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-WC-UTILIZATION-RATIO-001",
                        "wc_utilization_ratio",
                        "1.0",
                        "FACILITY",
                        "fac-4",
                        "VALUE",
                        "DECIMAL",
                        "0.95",
                        Instant.now().toString(),
                        Instant.now().toString(),
                        null,
                        null,
                        "COMPLETE",
                        "1.0");
        inputTopic.pipeInput("fac-4", toJson(featureValue));

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    private void pipeDelta(String facilityId, double delta) {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-WC-UTILIZATION-DELTA-30D-001",
                        "wc_utilization_delta_30d",
                        "1.0",
                        "FACILITY",
                        facilityId,
                        "VALUE",
                        "DECIMAL",
                        String.valueOf(delta),
                        Instant.now().toString(),
                        Instant.now().toString(),
                        null,
                        null,
                        "COMPLETE",
                        "1.0");
        inputTopic.pipeInput(facilityId, toJson(featureValue));
    }

    private static String toJson(JsonFeatureValue featureValue) {
        try {
            return MAPPER.writeValueAsString(featureValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonSignalDetected parse(String json) {
        try {
            return MAPPER.readValue(json, JsonSignalDetected.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
