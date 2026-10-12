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
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.ewsfi.avro.AvroPayloadCodec;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.ewsfi.contracts.interim.JsonSignalDetected;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link SignalPolicyTopology}'s policy evaluation in isolation using
 * {@link TopologyTestDriver} -- no broker required.
 */
class SignalPolicyTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AvroPayloadCodec codec = AvroPayloadCodec.forTests();

    private TopologyTestDriver driver;
    private TestInputTopic<String, byte[]> inputTopic;
    private TestOutputTopic<String, byte[]> outputTopic;

    @BeforeEach
    void setUp() throws Exception {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "signal-policy-topology-test-" + UUID.randomUUID());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.STATE_DIR_CONFIG, Files.createTempDirectory("signal-policy-test").toString());

        StreamsBuilder builder = new StreamsBuilder();
        new SignalPolicyTopology(codec).build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
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
    void thresholdMetProducesAProposedRepeatedPaymentReturnSignal() throws Exception {
        String featureValueId = UUID.randomUUID().toString();
        pipe("acct-1", featureValueJson(featureValueId, "acct-1", 3));

        List<TestRecord<String, byte[]>> outputs = outputTopic.readRecordsToList();

        assertThat(outputs).hasSize(1);
        JsonSignalDetected signal = signal(outputs.get(0));
        assertThat(signal.getSignalType()).isEqualTo("REPEATED_PAYMENT_RETURN");
        assertThat(signal.getStatus()).isEqualTo("PROPOSED");
        assertThat(signal.getEntityId()).isEqualTo("acct-1");
        assertThat(signal.getEvidenceIds()).containsExactly(featureValueId);
    }

    @Test
    void belowThresholdProducesNoSignal() {
        pipe("acct-2", featureValueJson(UUID.randomUUID().toString(), "acct-2", 2));

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void unrelatedFeatureNameProducesNoSignal() throws Exception {
        JsonFeatureValue other =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-OTHER",
                        "current_ratio",
                        "1.0",
                        "ACCOUNT",
                        "acct-3",
                        "VALUE",
                        "INTEGER",
                        "10",
                        Instant.now().toString(),
                        Instant.now().toString(),
                        Instant.now().toString(),
                        Instant.now().toString(),
                        "COMPLETE",
                        "1.0");
        pipe("acct-3", MAPPER.writeValueAsString(other));

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    private void pipe(String key, String featureJson) {
        AvroPayloadCodec.Encoded encoded = codec.encode("feature.value.updated", featureJson);
        inputTopic.pipeInput(new TestRecord<>(key, encoded.payload(), encoded.headers(), Instant.now()));
    }

    private JsonSignalDetected signal(TestRecord<String, byte[]> record) throws Exception {
        return MAPPER.readValue(codec.toInterimJson(record.value(), record.headers()), JsonSignalDetected.class);
    }

    private static String featureValueJson(String featureValueId, String accountId, long count) {
        Instant now = Instant.now();
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        featureValueId,
                        "FD-RETURNED-PAYMENT-COUNT-30D-001",
                        "returned_payment_count_30d",
                        "1.0",
                        "ACCOUNT",
                        accountId,
                        "VALUE",
                        "INTEGER",
                        String.valueOf(count),
                        now.toString(),
                        now.toString(),
                        now.minusSeconds(2592000).toString(),
                        now.toString(),
                        "COMPLETE",
                        "1.0");
        try {
            return MAPPER.writeValueAsString(featureValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
