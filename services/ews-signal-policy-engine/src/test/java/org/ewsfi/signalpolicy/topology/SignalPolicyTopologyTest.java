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
 * Tests {@link SignalPolicyTopology}'s policy evaluation in isolation using
 * {@link TopologyTestDriver} -- no broker required.
 */
class SignalPolicyTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "signal-policy-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new SignalPolicyTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        SignalPolicyTopology.FEATURE_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        SignalPolicyTopology.SIGNAL_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void thresholdMetProducesAProposedRepeatedPaymentReturnSignal() throws Exception {
        String featureValueId = UUID.randomUUID().toString();
        inputTopic.pipeInput(
                "acct-1", featureValueJson(featureValueId, "acct-1", 3), Instant.now());

        List<org.apache.kafka.streams.KeyValue<String, String>> outputs =
                outputTopic.readKeyValuesToList();

        assertThat(outputs).hasSize(1);
        JsonSignalDetected signal = MAPPER.readValue(outputs.get(0).value, JsonSignalDetected.class);
        assertThat(signal.getSignalType()).isEqualTo("REPEATED_PAYMENT_RETURN");
        assertThat(signal.getStatus()).isEqualTo("PROPOSED");
        assertThat(signal.getEntityId()).isEqualTo("acct-1");
        assertThat(signal.getEvidenceIds()).containsExactly(featureValueId);
    }

    @Test
    void belowThresholdProducesNoSignal() {
        inputTopic.pipeInput(
                "acct-2", featureValueJson(UUID.randomUUID().toString(), "acct-2", 2), Instant.now());

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void unrelatedFeatureNameProducesNoSignal() throws Exception {
        JsonFeatureValue other =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-OTHER",
                        "current_dpd",
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
        inputTopic.pipeInput("acct-3", MAPPER.writeValueAsString(other), Instant.now());

        assertThat(outputTopic.isEmpty()).isTrue();
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
