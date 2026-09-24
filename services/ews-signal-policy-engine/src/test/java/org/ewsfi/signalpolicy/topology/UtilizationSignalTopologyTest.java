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
 * Tests {@link UtilizationSignalTopology}'s policy evaluation in isolation using
 * {@link TopologyTestDriver} -- no broker required.
 */
class UtilizationSignalTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "utilization-signal-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new UtilizationSignalTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        UtilizationSignalTopology.FEATURE_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        UtilizationSignalTopology.SIGNAL_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void firesWhenUtilizationRatioMeetsTheThreshold() {
        pipeUtilization("fac-1", 0.92);

        List<String> outputs = outputTopic.readValuesToList();

        assertThat(outputs).hasSize(1);
        JsonSignalDetected signal = parse(outputs.get(0));
        assertThat(signal.getSignalType()).isEqualTo("UTILIZATION_HIGH");
        assertThat(signal.getEntityType()).isEqualTo("FACILITY");
        assertThat(signal.getEntityId()).isEqualTo("fac-1");
        assertThat(signal.getStatus()).isEqualTo("PROPOSED");
    }

    @Test
    void doesNotFireBelowTheThreshold() {
        pipeUtilization("fac-2", 0.5);

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void ignoresUnrelatedFeatureNames() {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-CURRENT-DPD-001",
                        "current_dpd",
                        "1.0",
                        "FACILITY",
                        "fac-3",
                        "VALUE",
                        "INTEGER",
                        "5",
                        Instant.now().toString(),
                        Instant.now().toString(),
                        null,
                        null,
                        "COMPLETE",
                        "1.0");
        inputTopic.pipeInput("fac-3", toJson(featureValue));

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void malformedNonNumericValueIsSkippedRatherThanCrashingTheTopology() {
        // Roadmap 3.6: a malformed valueNumeric must be filtered out, not thrown from inside the
        // stateless .filter() (which would crash-loop the stream thread on the record forever).
        JsonFeatureValue malformed =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-WC-UTILIZATION-RATIO-001",
                        "wc_utilization_ratio",
                        "1.0",
                        "FACILITY",
                        "fac-malformed",
                        "VALUE",
                        "DECIMAL",
                        "not-a-number",
                        Instant.now().toString(),
                        Instant.now().toString(),
                        null,
                        null,
                        "COMPLETE",
                        "1.0");
        inputTopic.pipeInput("fac-malformed", toJson(malformed));
        assertThat(outputTopic.isEmpty()).isTrue();

        pipeUtilization("fac-malformed", 0.95);
        assertThat(outputTopic.readValuesToList()).hasSize(1);
    }

    private void pipeUtilization(String facilityId, double ratio) {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-WC-UTILIZATION-RATIO-001",
                        "wc_utilization_ratio",
                        "1.0",
                        "FACILITY",
                        facilityId,
                        "VALUE",
                        "DECIMAL",
                        String.valueOf(ratio),
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
