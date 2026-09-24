package org.ewsfi.featureprocessor.topology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link MaxDpdFeatureTopology}'s logic in isolation using {@link TopologyTestDriver} -- no
 * broker required. Proves {@code max_dpd_30d} tracks the rolling maximum {@code currentDpd} within
 * the 30-day window, not the latest value (unlike {@link DpdFeatureTopologyTest}'s
 * {@code current_dpd}), and that a DPD dip does not lower the reported max within the window.
 */
class MaxDpdFeatureTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "max-dpd-feature-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new MaxDpdFeatureTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        MaxDpdFeatureTopology.CANONICAL_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        MaxDpdFeatureTopology.FEATURE_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void tracksTheRollingMaximumEvenAfterADpdDip() {
        Instant base = Instant.parse("2026-09-01T00:00:00Z");

        inputTopic.pipeInput("fac-1", dpdChangedJson("fac-1", 5), base);
        inputTopic.pipeInput("fac-1", dpdChangedJson("fac-1", 20), base.plusSeconds(60));
        // A later dip must not lower the reported rolling max within the window.
        inputTopic.pipeInput("fac-1", dpdChangedJson("fac-1", 8), base.plusSeconds(120));

        List<KeyValue<String, String>> outputs = outputTopic.readKeyValuesToList();

        int maxReported =
                outputs.stream()
                        .filter(kv -> "fac-1".equals(kv.key))
                        .mapToInt(kv -> Integer.parseInt(parse(kv.value).getValueNumeric()))
                        .max()
                        .orElseThrow();

        assertThat(maxReported).isEqualTo(20);
        assertThat(outputs)
                .allSatisfy(kv -> assertThat(parse(kv.value).getFeatureName()).isEqualTo("max_dpd_30d"));
    }

    @Test
    void computesMaxIndependentlyPerFacility() {
        inputTopic.pipeInput("fac-a", dpdChangedJson("fac-a", 15), Instant.now());
        inputTopic.pipeInput("fac-b", dpdChangedJson("fac-b", 40), Instant.now());

        List<KeyValue<String, String>> outputs = outputTopic.readKeyValuesToList();

        int maxForA =
                outputs.stream()
                        .filter(kv -> "fac-a".equals(kv.key))
                        .mapToInt(kv -> Integer.parseInt(parse(kv.value).getValueNumeric()))
                        .max()
                        .orElseThrow();
        int maxForB =
                outputs.stream()
                        .filter(kv -> "fac-b".equals(kv.key))
                        .mapToInt(kv -> Integer.parseInt(parse(kv.value).getValueNumeric()))
                        .max()
                        .orElseThrow();

        assertThat(maxForA).isEqualTo(15);
        assertThat(maxForB).isEqualTo(40);
    }

    @Test
    void aMalformedNonNumericCurrentDpdIsSkippedRatherThanCrashingTheAggregator() {
        // Roadmap 3.6: before the defensive hasNumericCurrentDpd filter was added, this record
        // reached extractCurrentDpd inside the .aggregate() call and threw a ClassCastException,
        // crashing the stream thread. Kafka's at-least-once redelivery means an uncaught exception
        // there does not skip the record even with REPLACE_THREAD configured -- the replacement
        // thread just re-reads and re-crashes on the same offset forever. The only real fix is to
        // never let the malformed record reach the aggregator in the first place.
        Map<String, Object> malformedData = new LinkedHashMap<>();
        malformedData.put("facilityId", "fac-malformed");
        malformedData.put("counterpartyId", "cp-1");
        malformedData.put("previousDpd", null);
        malformedData.put("currentDpd", "not-a-number");
        malformedData.put("asOfDate", "2026-09-24");
        JsonEventEnvelope malformedEnvelope =
                new JsonEventEnvelope(
                        UUID.randomUUID().toString(),
                        "obligation.dpd.changed",
                        Instant.now().toString(),
                        "FACILITY",
                        "fac-malformed",
                        malformedData);

        inputTopic.pipeInput("fac-malformed", toJson(malformedEnvelope), Instant.now());
        assertThat(outputTopic.isEmpty())
                .as("a malformed currentDpd must be silently filtered out, not crash the topology")
                .isTrue();

        // A subsequent valid record on the same facility must still be processed normally,
        // proving the filter doesn't just avoid a crash but leaves the topology fully functional.
        inputTopic.pipeInput("fac-malformed", dpdChangedJson("fac-malformed", 17), Instant.now());
        List<KeyValue<String, String>> outputs = outputTopic.readKeyValuesToList();
        assertThat(outputs)
                .anySatisfy(
                        kv -> {
                            assertThat(kv.key).isEqualTo("fac-malformed");
                            assertThat(parse(kv.value).getValueNumeric()).isEqualTo("17");
                        });
    }

    @Test
    void unrelatedEventTypesProduceNoOutput() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("something", "else");
        JsonEventEnvelope envelope =
                new JsonEventEnvelope(
                        "evt-1", "financial.statement.received", Instant.now().toString(), "FACILITY",
                        "fac-2", data);

        inputTopic.pipeInput("fac-2", toJson(envelope), Instant.now());

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    private static String dpdChangedJson(String facilityId, int currentDpd) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("facilityId", facilityId);
        data.put("counterpartyId", "cp-1");
        data.put("previousDpd", null);
        data.put("currentDpd", currentDpd);
        data.put("asOfDate", "2026-09-24");
        JsonEventEnvelope envelope =
                new JsonEventEnvelope(
                        UUID.randomUUID().toString(),
                        "obligation.dpd.changed",
                        Instant.now().toString(),
                        "FACILITY",
                        facilityId,
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
