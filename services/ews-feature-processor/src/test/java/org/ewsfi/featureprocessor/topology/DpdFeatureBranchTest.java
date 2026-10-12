package org.ewsfi.featureprocessor.topology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
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

class DpdFeatureBranchTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AvroPayloadCodec codec = AvroPayloadCodec.forTests();

    private TopologyTestDriver driver;
    private TestInputTopic<String, byte[]> inputTopic;
    private TestOutputTopic<String, byte[]> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "dpd-feature-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new FeatureProcessorTopology(codec).build(builder);
        driver = new TopologyTestDriver(builder.build(), props);
        inputTopic =
                driver.createInputTopic(
                        DpdFeatureBranch.REPAYMENT_TOPIC,
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
    void reconciledDpdEmitsCurrentValueAndRollingMaxAndDoesNotDefaultUnreconciledToZero() throws Exception {
        Instant start = Instant.parse("2026-09-01T00:00:00Z");
        pipe("fac-1", dpdJson("evt-1", "fac-1", 0, 10, true, start), start);
        pipe("fac-1", dpdJson("evt-2", "fac-1", 10, 40, true, start.plus(Duration.ofDays(10))), start.plus(Duration.ofDays(10)));
        pipe("fac-1", dpdJson("evt-3", "fac-1", 40, 999, false, start.plus(Duration.ofDays(11))), start.plus(Duration.ofDays(11)));

        List<JsonFeatureValue> values = outputTopic.readRecordsToList().stream().map(this::parse).toList();
        List<JsonFeatureValue> current =
                values.stream().filter(value -> "current_dpd".equals(value.getFeatureName())).toList();

        assertThat(current).hasSize(3);
        assertThat(current.get(0).getValueNumeric()).isEqualTo("10");
        assertThat(current.get(0).getState()).isEqualTo("VALUE");
        assertThat(current.get(0).getPriorValueNumeric()).isEqualTo("0");
        assertThat(current.get(0).getEntityType()).isEqualTo("FACILITY");
        assertThat(current.get(1).getValueNumeric()).isEqualTo("40");
        assertThat(current.get(2).getState()).isEqualTo("INVALID");
        assertThat(current.get(2).getValueNumeric()).isNull();
        assertThat(current.get(2).getQualityState()).isEqualTo("INSUFFICIENT");

        long max =
                values.stream()
                        .filter(value -> "max_dpd_30d".equals(value.getFeatureName()))
                        .mapToLong(value -> Long.parseLong(value.getValueNumeric()))
                        .max()
                        .orElseThrow();
        assertThat(max).isEqualTo(40);
        assertThat(values).noneMatch(value -> "999".equals(value.getValueNumeric()));
    }

    @Test
    void zeroDpdIsExplicitZeroRatherThanMissing() throws Exception {
        Instant at = Instant.parse("2026-09-02T00:00:00Z");
        pipe("fac-2", dpdJson("evt-zero", "fac-2", null, 0, true, at), at);

        JsonFeatureValue current =
                outputTopic.readRecordsToList().stream()
                        .map(this::parse)
                        .filter(value -> "current_dpd".equals(value.getFeatureName()))
                        .findFirst()
                        .orElseThrow();
        assertThat(current.getState()).isEqualTo("ZERO");
        assertThat(current.getValueNumeric()).isEqualTo("0");
        assertThat(current.getPriorValueNumeric()).isNull();
    }

    @Test
    void unrelatedEventTypesProduceNoDpdFeature() throws Exception {
        AvroPayloadCodec.Encoded encoded =
                codec.encode("payment.instruction.returned", MAPPER.writeValueAsString(paymentOnWrongTopic()));
        inputTopic.pipeInput(
                new TestRecord<>(
                        "fac-3", encoded.payload(), encoded.headers(), Instant.parse("2026-09-03T00:00:00Z")));

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    private static String dpdJson(
            String eventId, String facilityId, Integer previous, int current, boolean reconciled, Instant eventTime)
            throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("facilityId", facilityId);
        data.put("counterpartyId", "cpty-" + facilityId);
        data.put("previousDpd", previous);
        data.put("currentDpd", current);
        data.put("asOfDate", "2026-09-01");
        data.put("calculationPolicyRef", "POL-DPD-CALC-001");
        data.put("reconciled", reconciled);
        JsonEventEnvelope envelope =
                new JsonEventEnvelope(
                        eventId, DpdFeatureBranch.EVENT_TYPE, eventTime.toString(), "FACILITY", facilityId, data);
        return MAPPER.writeValueAsString(envelope);
    }

    private void pipe(String key, String envelopeJson, Instant time) throws Exception {
        AvroPayloadCodec.Encoded encoded = codec.encode("obligation.dpd.changed", envelopeJson);
        inputTopic.pipeInput(new TestRecord<>(key, encoded.payload(), encoded.headers(), time));
    }

    private JsonFeatureValue parse(TestRecord<String, byte[]> record) {
        try {
            return MAPPER.readValue(codec.toInterimJson(record.value(), record.headers()), JsonFeatureValue.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonEventEnvelope paymentOnWrongTopic() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paymentInstructionId", "pi-other");
        data.put("accountId", "acct-other");
        data.put("amount", "1.00");
        data.put("currency", "GBP");
        data.put("returnReasonCode", "R01");
        data.put("returnReasonCategory", "FINANCIAL");
        data.put("returnDate", "2026-09-03");
        return new JsonEventEnvelope(
                "evt-other",
                "payment.instruction.returned",
                "2026-09-03T00:00:00Z",
                "ACCOUNT",
                "acct-other",
                data);
    }
}
