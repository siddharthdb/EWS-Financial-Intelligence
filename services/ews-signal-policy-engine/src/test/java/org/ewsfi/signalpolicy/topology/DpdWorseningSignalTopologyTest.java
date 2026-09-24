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
 * Tests {@link DpdWorseningSignalTopology}'s policy evaluation in isolation using
 * {@link TopologyTestDriver} -- no broker required. Proves DPD_WORSENING fires exactly on a
 * material (>= 10 day) increase in {@code max_dpd_30d}, and does not fire on an unchanged or only
 * slightly increased value.
 */
class DpdWorseningSignalTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "dpd-worsening-signal-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new DpdWorseningSignalTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        DpdWorseningSignalTopology.FEATURE_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        DpdWorseningSignalTopology.SIGNAL_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void firesOnAMaterialIncreaseInMaxDpd30d() {
        pipeMaxDpd("fac-1", 5);
        pipeMaxDpd("fac-1", 20); // +15, above the 10-day threshold

        List<String> outputs = outputTopic.readValuesToList();

        assertThat(outputs).hasSize(1);
        JsonSignalDetected signal = parse(outputs.get(0));
        assertThat(signal.getSignalType()).isEqualTo("DPD_WORSENING");
        assertThat(signal.getEntityType()).isEqualTo("FACILITY");
        assertThat(signal.getEntityId()).isEqualTo("fac-1");
        assertThat(signal.getStatus()).isEqualTo("PROPOSED");
    }

    @Test
    void doesNotFireOnASmallIncreaseBelowTheThreshold() {
        pipeMaxDpd("fac-2", 10);
        pipeMaxDpd("fac-2", 15); // +5, below the 10-day threshold

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void doesNotEmitUnchangedDailyDuplicates() {
        pipeMaxDpd("fac-3", 30);
        pipeMaxDpd("fac-3", 30); // unchanged

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void malformedNonNumericValueLeavesStateUnchangedRatherThanCrashingTheTopology() {
        // Roadmap 3.6: a malformed valueNumeric inside the stateful .aggregate() must leave the
        // transition state unchanged, not throw (which would crash-loop the stream thread forever).
        pipeMaxDpd("fac-malformed", 5);

        JsonFeatureValue malformed =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-MAX-DPD-30D-001",
                        "max_dpd_30d",
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

        // A subsequent genuine material increase (from the pre-malformed-record value of 5) must
        // still fire correctly, proving the malformed record didn't corrupt the transition state.
        pipeMaxDpd("fac-malformed", 20);
        List<String> outputs = outputTopic.readValuesToList();
        assertThat(outputs).hasSize(1);
        assertThat(parse(outputs.get(0)).getSignalType()).isEqualTo("DPD_WORSENING");
    }

    private void pipeMaxDpd(String facilityId, int max) {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-MAX-DPD-30D-001",
                        "max_dpd_30d",
                        "1.0",
                        "FACILITY",
                        facilityId,
                        "VALUE",
                        "INTEGER",
                        String.valueOf(max),
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
