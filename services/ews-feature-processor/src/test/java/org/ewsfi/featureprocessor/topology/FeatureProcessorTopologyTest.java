package org.ewsfi.featureprocessor.topology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.test.TestRecord;
import org.apache.kafka.streams.TopologyTestDriver;
import org.ewsfi.avro.AvroPayloadCodec;
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
    private final AvroPayloadCodec codec = AvroPayloadCodec.forTests();

    private TopologyTestDriver driver;
    private TestInputTopic<String, byte[]> inputTopic;
    private TestOutputTopic<String, byte[]> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "feature-processor-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new FeatureProcessorTopology(codec).build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        FeatureProcessorTopology.CANONICAL_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.ByteArray().serializer());
        outputTopic =
                driver.createOutputTopic(
                        FeatureProcessorTopology.FEATURE_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.ByteArray().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void countsOnlyNonExcludedPaymentReturnsWithinTheRollingWindow() throws Exception {
        Instant base = Instant.parse("2026-09-01T00:00:00Z");

        pipe("acct-1", paymentReturnJson("acct-1", "FINANCIAL"), base);
        pipe("acct-1", paymentReturnJson("acct-1", "FINANCIAL"), base.plusSeconds(60));
        // Technical/beneficiary-detail returns are excluded per P03's reason-code policy.
        pipe("acct-1", paymentReturnJson("acct-1", "TECHNICAL"), base.plusSeconds(120));
        pipe("acct-1", paymentReturnJson("acct-1", "BENEFICIARY_DETAIL"), base.plusSeconds(150));
        pipe("acct-1", paymentReturnJson("acct-1", "FINANCIAL"), base.plusSeconds(180));

        List<TestRecord<String, byte[]>> outputs = outputTopic.readRecordsToList();

        long maxCount =
                outputs.stream()
                        .filter(record -> "acct-1".equals(record.key()))
                        .mapToLong(record -> Long.parseLong(parse(record).getValueNumeric()))
                        .max()
                        .orElseThrow();

        assertThat(maxCount).isEqualTo(3);
        assertThat(outputs).allSatisfy(record -> assertThat(parse(record).getFeatureName())
                .isEqualTo("returned_payment_count_30d"));
    }

    @Test
    void unrelatedEventTypesProduceNoOutput() {
        inputTopic.pipeInput("acct-2", new byte[] {1, 2, 3}, Instant.now());

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    private static String paymentReturnJson(String accountId, String returnReasonCategory) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paymentInstructionId", "pi-" + System.nanoTime());
        data.put("accountId", accountId);
        data.put("amount", "10.00");
        data.put("currency", "GBP");
        data.put("returnReasonCode", "R01");
        data.put("returnReasonCategory", returnReasonCategory);
        data.put("returnDate", "2026-09-01");
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

    private void pipe(String key, String envelopeJson, Instant time) {
        AvroPayloadCodec.Encoded encoded = codec.encode("payment.instruction.returned", envelopeJson);
        inputTopic.pipeInput(new TestRecord<>(key, encoded.payload(), encoded.headers(), time));
    }

    private JsonFeatureValue parse(TestRecord<String, byte[]> record) {
        try {
            return MAPPER.readValue(codec.toInterimJson(record.value(), record.headers()), JsonFeatureValue.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
