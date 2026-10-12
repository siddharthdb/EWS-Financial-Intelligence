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
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.test.TestRecord;
import org.ewsfi.avro.AvroPayloadCodec;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.ewsfi.contracts.interim.JsonSignalDetected;
import org.ewsfi.signalpolicy.policy.UtilizationSignalPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UtilizationSignalTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AvroPayloadCodec codec = AvroPayloadCodec.forTests();
    private TopologyTestDriver driver;
    private TestInputTopic<String, byte[]> inputTopic;
    private TestOutputTopic<String, byte[]> outputTopic;

    @BeforeEach
    void setUp() throws Exception {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "utilization-signal-" + UUID.randomUUID());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.STATE_DIR_CONFIG, Files.createTempDirectory("utilization-signal").toString());
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
    void crossingHighAndALargeJumpProposeBothSignals() throws Exception {
        pipe("fac-1", "0.70");
        pipe("fac-1", "0.95");

        List<String> types = signals().stream().map(JsonSignalDetected::getSignalType).toList();
        assertThat(types).containsExactly(UtilizationSignalPolicy.HIGH, UtilizationSignalPolicy.SPIKE);
    }

    @Test
    void stayingHighDoesNotOpenAnotherHighSignal() throws Exception {
        pipe("fac-2", "0.95");
        pipe("fac-2", "0.96");
        assertThat(signals()).extracting(JsonSignalDetected::getSignalType).containsExactly(UtilizationSignalPolicy.HIGH);
    }

    private void pipe(String facilityId, String ratio) throws Exception {
        JsonFeatureValue feature =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-WC-UTILIZATION-RATIO-001",
                        "wc_utilization_ratio",
                        "1.0",
                        "FACILITY",
                        facilityId,
                        "VALUE",
                        "DECIMAL",
                        ratio,
                        "2026-10-01T00:00:00Z",
                        "2026-10-01T00:00:00Z",
                        "2026-10-01T00:00:00Z",
                        "2026-10-01T00:00:00Z",
                        "COMPLETE",
                        "1.0");
        AvroPayloadCodec.Encoded encoded = codec.encode("feature.value.updated", MAPPER.writeValueAsString(feature));
        inputTopic.pipeInput(
                new TestRecord<>(facilityId, encoded.payload(), encoded.headers(), Instant.parse("2026-10-01T00:00:00Z")));
    }

    private List<JsonSignalDetected> signals() {
        return outputTopic.readRecordsToList().stream()
                .map(
                        record -> {
                            try {
                                return MAPPER.readValue(
                                        codec.toInterimJson(record.value(), record.headers()), JsonSignalDetected.class);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .toList();
    }
}
