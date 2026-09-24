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
 * Tests {@link DpdSignalTopology}'s policy evaluation in isolation using
 * {@link TopologyTestDriver} -- no broker required. Proves DPD_EMERGED fires exactly on a genuine
 * 0-or-unknown to positive DPD transition, and does not fire on other transitions.
 */
class DpdSignalTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "dpd-signal-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new DpdSignalTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        DpdSignalTopology.FEATURE_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        DpdSignalTopology.SIGNAL_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void firesExactlyOnAGenuineZeroToPositiveTransition() {
        // fac-1: unknown -> 0 (no signal: currentDpd is 0, not > 0) -> 5 (signal: emerged).
        pipeCurrentDpd("fac-1", 0);
        pipeCurrentDpd("fac-1", 5);

        List<String> outputs = outputTopic.readValuesToList();

        assertThat(outputs).hasSize(1);
        JsonSignalDetected signal = parse(outputs.get(0));
        assertThat(signal.getSignalType()).isEqualTo("DPD_EMERGED");
        assertThat(signal.getEntityType()).isEqualTo("FACILITY");
        assertThat(signal.getEntityId()).isEqualTo("fac-1");
        assertThat(signal.getStatus()).isEqualTo("PROPOSED");
    }

    @Test
    void doesNotFireOnAWorseningTransitionBetweenTwoPositiveValues() {
        pipeCurrentDpd("fac-2", 5);
        // Consume the DPD_EMERGED signal from the 0(unknown)->5 transition above.
        outputTopic.readValuesToList();

        pipeCurrentDpd("fac-2", 12);

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void doesNotFireOnAZeroToZeroNoOp() {
        pipeCurrentDpd("fac-3", 0);
        pipeCurrentDpd("fac-3", 0);

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void malformedNonNumericValueLeavesStateUnchangedRatherThanCrashingTheTopology() {
        // Roadmap 3.6: a malformed valueNumeric inside the stateful .aggregate() must leave the
        // transition state unchanged, not throw (which would crash-loop the stream thread forever).
        JsonFeatureValue malformed =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-CURRENT-DPD-001",
                        "current_dpd",
                        "1.0",
                        "FACILITY",
                        "fac-malformed",
                        "VALUE",
                        "INTEGER",
                        "not-a-number",
                        Instant.now().toString(),
                        Instant.now().toString(),
                        null,
                        null,
                        "COMPLETE",
                        "1.0");
        inputTopic.pipeInput("fac-malformed", toJson(malformed));
        assertThat(outputTopic.isEmpty()).isTrue();

        // A subsequent genuine 0-or-unknown -> positive transition on the same facility must still
        // fire correctly, proving the malformed record didn't corrupt the transition state.
        pipeCurrentDpd("fac-malformed", 7);
        List<String> outputs = outputTopic.readValuesToList();
        assertThat(outputs).hasSize(1);
        assertThat(parse(outputs.get(0)).getSignalType()).isEqualTo("DPD_EMERGED");
    }

    private void pipeCurrentDpd(String facilityId, int currentDpd) {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-CURRENT-DPD-001",
                        "current_dpd",
                        "1.0",
                        "FACILITY",
                        facilityId,
                        "VALUE",
                        "INTEGER",
                        String.valueOf(currentDpd),
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
