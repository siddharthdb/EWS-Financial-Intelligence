package org.ewsfi.featureprocessor.topology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FeatureProcessorTopology}'s logic in isolation using Kafka Streams'
 * {@link TopologyTestDriver} -- fully in-process, no broker (embedded or otherwise) required.
 */
class FeatureProcessorTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "feature-processor-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new FeatureProcessorTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        FeatureProcessorTopology.CANONICAL_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        FeatureProcessorTopology.FEATURE_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void countsOnlyNonExcludedPaymentReturnsWithinTheRollingWindow() throws Exception {
        Instant base = Instant.parse("2026-09-01T00:00:00Z");

        inputTopic.pipeInput("acct-1", paymentReturnJson("acct-1", "FINANCIAL"), base);
        inputTopic.pipeInput("acct-1", paymentReturnJson("acct-1", "FINANCIAL"), base.plusSeconds(60));
        // Technical/beneficiary-detail returns are excluded per P03's reason-code policy.
        inputTopic.pipeInput("acct-1", paymentReturnJson("acct-1", "TECHNICAL"), base.plusSeconds(120));
        inputTopic.pipeInput(
                "acct-1", paymentReturnJson("acct-1", "BENEFICIARY_DETAIL"), base.plusSeconds(150));
        inputTopic.pipeInput("acct-1", paymentReturnJson("acct-1", "FINANCIAL"), base.plusSeconds(180));

        List<KeyValue<String, String>> outputs = outputTopic.readKeyValuesToList();

        long maxCount =
                outputs.stream()
                        .filter(kv -> "acct-1".equals(kv.key))
                        .mapToLong(kv -> Long.parseLong(parse(kv.value).getValueNumeric()))
                        .max()
                        .orElseThrow();

        assertThat(maxCount).isEqualTo(3);
        assertThat(outputs).allSatisfy(kv -> assertThat(parse(kv.value).getFeatureName())
                .isEqualTo("returned_payment_count_30d"));
    }

    @Test
    void unrelatedEventTypesProduceNoOutput() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("something", "else");
        JsonEventEnvelope envelope =
                new JsonEventEnvelope(
                        "evt-1", "financial.statement.received", Instant.now().toString(), "ACCOUNT",
                        "acct-2", data);

        inputTopic.pipeInput("acct-2", toJson(envelope), Instant.now());

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    private static String paymentReturnJson(String accountId, String returnReasonCategory) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paymentInstructionId", "pi-" + System.nanoTime());
        data.put("accountId", accountId);
        data.put("returnReasonCategory", returnReasonCategory);
        JsonEventEnvelope envelope =
                new JsonEventEnvelope(
                        java.util.UUID.randomUUID().toString(),
                        "payment.instruction.returned",
                        Instant.now().toString(),
                        "ACCOUNT",
                        accountId,
                        data);
        return toJson(envelope);
    }

    private static String toJson(JsonEventEnvelope envelope) {
        try {
            return MAPPER.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonFeatureValue parse(String json) {
        try {
            return MAPPER.readValue(json, JsonFeatureValue.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
