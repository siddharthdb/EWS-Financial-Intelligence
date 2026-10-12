package org.ewsfi.signalpolicy.topology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.test.TestRecord;
import org.apache.kafka.streams.TopologyTestDriver;
import org.ewsfi.avro.AvroPayloadCodec;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.ewsfi.contracts.interim.JsonSignalDetected;
import org.ewsfi.signalpolicy.policy.DpdSignalPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DpdSignalTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AvroPayloadCodec codec = AvroPayloadCodec.forTests();

    private TopologyTestDriver driver;
    private TestInputTopic<String, byte[]> inputTopic;
    private TestOutputTopic<String, byte[]> outputTopic;

    @BeforeEach
    void setUp() throws Exception {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "dpd-signal-topology-" + UUID.randomUUID());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.STATE_DIR_CONFIG, Files.createTempDirectory("dpd-signal-test").toString());

        StreamsBuilder builder = new StreamsBuilder();
        new SignalPolicyTopology(codec).build(builder);
        driver = new TopologyTestDriver(builder.build(), props);
        inputTopic =
                driver.createInputTopic(
                        SignalPolicyTopology.FEATURE_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.ByteArray().serializer());
        outputTopic =
                driver.createOutputTopic(
                        SignalPolicyTopology.SIGNAL_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.ByteArray().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void proposesEmergedAndWorseningOnlyOnBandCrossings() throws Exception {
        pipe("fac-1", feature("fv-0", "fac-1", "0", "ZERO", null));
        pipe("fac-1", feature("fv-1", "fac-1", "12", "VALUE", "0"));
        pipe("fac-1", feature("fv-2", "fac-1", "20", "VALUE", "12"));
        pipe("fac-1", feature("fv-3", "fac-1", "45", "VALUE", "20"));
        pipe("fac-1", feature("fv-4", "fac-1", "45", "VALUE", "45"));
        pipe("fac-1", feature("fv-5", "fac-1", "0", "ZERO", "45"));
        pipe("fac-1", feature("fv-6", "fac-1", "8", "VALUE", "0"));

        List<JsonSignalDetected> signals = signals();
        assertThat(signals).extracting(JsonSignalDetected::getSignalType)
                .containsExactly(DpdSignalPolicy.EMERGED, DpdSignalPolicy.WORSENING, DpdSignalPolicy.EMERGED);
        assertThat(signals).allSatisfy(signal -> {
            assertThat(signal.getStatus()).isEqualTo("PROPOSED");
            assertThat(signal.getEntityType()).isEqualTo("FACILITY");
            assertThat(signal.getEntityId()).isEqualTo("fac-1");
            assertThat(signal.getEvidenceIds()).isNotEmpty();
        });
        assertThat(signals.get(1).getSeverity()).isEqualTo("MEDIUM");
        assertThat(signals.get(1).getPolicyId()).isEqualTo(DpdSignalPolicy.WORSENING_POLICY_ID);
    }

    @Test
    void firstObservationAlreadyInsideABandDoesNotLookLikeEmergence() throws Exception {
        pipe("fac-2", feature("fv-known", "fac-2", "45", "VALUE", "40"));

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void invalidDpdDoesNotResetThePriorBand() throws Exception {
        pipe("fac-3", feature("fv-a", "fac-3", "10", "VALUE", "0"));
        pipe("fac-3", feature("fv-b", "fac-3", null, "INVALID", "10"));
        pipe("fac-3", feature("fv-c", "fac-3", "15", "VALUE", null));

        List<JsonSignalDetected> signals = signals();
        assertThat(signals).extracting(JsonSignalDetected::getSignalType).containsExactly(DpdSignalPolicy.EMERGED);
    }

    private void pipe(String key, String json) {
        AvroPayloadCodec.Encoded encoded = codec.encode("feature.value.updated", json);
        inputTopic.pipeInput(
                new TestRecord<>(key, encoded.payload(), encoded.headers(), Instant.parse("2026-09-01T00:00:00Z")));
    }

    private List<JsonSignalDetected> signals() {
        return outputTopic.readRecordsToList().stream().map(this::parse).toList();
    }

    private static String feature(
            String featureValueId, String facilityId, String current, String state, String prior) {
        JsonFeatureValue feature =
                new JsonFeatureValue(
                        featureValueId,
                        "FD-CURRENT-DPD-001",
                        "current_dpd",
                        "1.0",
                        "FACILITY",
                        facilityId,
                        state,
                        "INTEGER",
                        current,
                        "2026-09-01T00:00:00Z",
                        "2026-09-01T00:00:00Z",
                        "2026-09-01T00:00:00Z",
                        "2026-09-01T00:00:00Z",
                        "INVALID".equals(state) ? "INSUFFICIENT" : "COMPLETE",
                        "1.0");
        feature.setPriorValueNumeric(prior);
        try {
            return MAPPER.writeValueAsString(feature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonSignalDetected parse(TestRecord<String, byte[]> record) {
        try {
            return MAPPER.readValue(codec.toInterimJson(record.value(), record.headers()), JsonSignalDetected.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
