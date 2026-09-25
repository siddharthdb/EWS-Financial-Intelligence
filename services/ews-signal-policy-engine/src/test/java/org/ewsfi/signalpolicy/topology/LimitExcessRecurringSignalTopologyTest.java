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
 * Tests {@link LimitExcessRecurringSignalTopology}'s policy evaluation in isolation using
 * {@link TopologyTestDriver} -- no broker required. Proves LIMIT_EXCESS_RECURRING fires only once
 * two *consecutive* observations both show negative headroom (excess), not on a single one-off
 * excess or a recovery back to positive headroom.
 */
class LimitExcessRecurringSignalTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "limit-excess-recurring-signal-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new LimitExcessRecurringSignalTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        LimitExcessRecurringSignalTopology.FEATURE_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        LimitExcessRecurringSignalTopology.SIGNAL_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void doesNotFireOnASingleOneOffExcess() {
        pipeHeadroom("fac-1", 5000.0);
        pipeHeadroom("fac-1", -2000.0); // first negative -- a one-off excess, not yet recurring

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void firesWhenTwoConsecutiveObservationsAreBothNegative() {
        pipeHeadroom("fac-2", -1000.0);
        pipeHeadroom("fac-2", -3000.0); // second consecutive negative -- recurring

        List<String> outputs = outputTopic.readValuesToList();

        assertThat(outputs).hasSize(1);
        JsonSignalDetected signal = parse(outputs.get(0));
        assertThat(signal.getSignalType()).isEqualTo("LIMIT_EXCESS_RECURRING");
        assertThat(signal.getEntityType()).isEqualTo("FACILITY");
        assertThat(signal.getEntityId()).isEqualTo("fac-2");
        assertThat(signal.getStatus()).isEqualTo("PROPOSED");
    }

    @Test
    void doesNotFireOnceRecoveredToPositiveHeadroom() {
        pipeHeadroom("fac-3", -1000.0);
        pipeHeadroom("fac-3", -3000.0);
        outputTopic.readValuesToList(); // drain the recurring signal from above

        pipeHeadroom("fac-3", 500.0); // recovered -- no longer in excess

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void firesAgainOnAThirdConsecutiveNegativeObservation() {
        // Each new qualifying consecutive pair fires -- an ongoing excess episode keeps re-firing,
        // per this topology's documented "not only the first crossing" precedent.
        pipeHeadroom("fac-4", -1000.0);
        pipeHeadroom("fac-4", -2000.0);
        outputTopic.readValuesToList(); // drain the first recurring signal

        pipeHeadroom("fac-4", -3000.0);

        assertThat(outputTopic.readValuesToList()).hasSize(1);
    }

    private void pipeHeadroom(String facilityId, double headroom) {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-WC-AVAILABLE-HEADROOM-001",
                        "wc_available_headroom",
                        "1.0",
                        "FACILITY",
                        facilityId,
                        "VALUE",
                        "DECIMAL",
                        String.valueOf(headroom),
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
