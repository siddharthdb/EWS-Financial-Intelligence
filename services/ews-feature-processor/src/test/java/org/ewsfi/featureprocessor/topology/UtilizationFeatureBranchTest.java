package org.ewsfi.featureprocessor.topology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.test.TestRecord;
import org.ewsfi.avro.AvroPayloadCodec;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UtilizationFeatureBranchTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AvroPayloadCodec codec = AvroPayloadCodec.forTests();

    private TopologyTestDriver driver;
    private TestInputTopic<String, byte[]> inputTopic;
    private TestOutputTopic<String, byte[]> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "utilization-feature-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        StreamsBuilder builder = new StreamsBuilder();
        new FeatureProcessorTopology(codec).build(builder);
        driver = new TopologyTestDriver(builder.build(), props);
        inputTopic =
                driver.createInputTopic(
                        UtilizationFeatureBranch.FACILITY_TOPIC,
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
    void limitAndOutstandingProduceRatioAndHeadroom() throws Exception {
        pipe(limit("fac-1", "1000.00", "GBP"));
        pipe(outstanding("fac-1", "950.00", "GBP"));

        Map<String, JsonFeatureValue> features = latestByName();
        assertThat(new BigDecimal(features.get("wc_utilization_ratio").getValueNumeric()))
                .isEqualByComparingTo("0.95");
        assertThat(new BigDecimal(features.get("wc_available_headroom").getValueNumeric()))
                .isEqualByComparingTo("50.00");
        assertThat(features.get("wc_utilization_ratio").getState()).isEqualTo("VALUE");
    }

    @Test
    void nonPositiveCapacityIsInvalid() throws Exception {
        pipe(limit("fac-2", "0.00", "GBP"));
        pipe(outstanding("fac-2", "10.00", "GBP"));

        Map<String, JsonFeatureValue> features = latestByName();
        assertThat(features.get("wc_utilization_ratio").getState()).isEqualTo("INVALID");
        assertThat(features.get("wc_utilization_ratio").getValueNumeric()).isNull();
        assertThat(features.get("wc_available_headroom").getState()).isEqualTo("INVALID");
    }

    private Map<String, JsonFeatureValue> latestByName() throws Exception {
        Map<String, JsonFeatureValue> features = new LinkedHashMap<>();
        for (TestRecord<String, byte[]> record : outputTopic.readRecordsToList()) {
            JsonFeatureValue value =
                    MAPPER.readValue(codec.toInterimJson(record.value(), record.headers()), JsonFeatureValue.class);
            features.put(value.getFeatureName(), value);
        }
        return features;
    }

    private void pipe(String envelopeJson) {
        String eventType = envelopeJson.contains("facility.limit.changed")
                ? "facility.limit.changed"
                : "facility.outstanding.changed";
        AvroPayloadCodec.Encoded encoded = codec.encode(eventType, envelopeJson);
        inputTopic.pipeInput(new TestRecord<>("ignored", encoded.payload(), encoded.headers(), Instant.parse("2026-10-01T00:00:00Z")));
    }

    private static String limit(String facilityId, String amount, String currency) throws Exception {
        return envelope("evt-limit-" + facilityId, "facility.limit.changed", facilityId, Map.of(
                "facilityId", facilityId,
                "counterpartyId", "cpty-" + facilityId,
                "sanctionedLimit", amount,
                "currency", currency,
                "capacityRule", "SANCTIONED_LIMIT"));
    }

    private static String outstanding(String facilityId, String amount, String currency) throws Exception {
        return envelope("evt-out-" + facilityId, "facility.outstanding.changed", facilityId, Map.of(
                "facilityId", facilityId,
                "counterpartyId", "cpty-" + facilityId,
                "eligibleOutstanding", amount,
                "currency", currency));
    }

    private static String envelope(String eventId, String eventType, String facilityId, Map<String, String> data)
            throws Exception {
        return MAPPER.writeValueAsString(
                new JsonEventEnvelope(eventId, eventType, "2026-10-01T00:00:00Z", "FACILITY", facilityId, new LinkedHashMap<>(data)));
    }
}
