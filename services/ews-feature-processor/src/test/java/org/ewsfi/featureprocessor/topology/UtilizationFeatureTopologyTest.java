package org.ewsfi.featureprocessor.topology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
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
 * Tests {@link UtilizationFeatureTopology}'s join logic in isolation using
 * {@link TopologyTestDriver} -- no broker required. Proves {@code wc_utilization_ratio} is only
 * emitted once both a limit and an outstanding observation exist for a facility, is recomputed
 * when either side changes, and is computed independently per facility.
 */
class UtilizationFeatureTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "utilization-feature-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new UtilizationFeatureTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        UtilizationFeatureTopology.CANONICAL_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        UtilizationFeatureTopology.FEATURE_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void emitsOnlyOnceBothLimitAndOutstandingAreKnownThenRecomputesOnChange() {
        Instant base = Instant.parse("2026-09-01T00:00:00Z");

        // Limit-only: no output yet, since outstanding hasn't been observed.
        inputTopic.pipeInput("fac-1", limitChangedJson("fac-1", 100000.0), base);
        assertThat(outputTopic.isEmpty()).isTrue();

        // Now both sides are known -> first emission.
        inputTopic.pipeInput("fac-1", outstandingChangedJson("fac-1", 80000.0), base.plusSeconds(60));
        var outputs = outputTopic.readKeyValuesToList();
        assertThat(outputs).hasSize(1);
        JsonFeatureValue first = parse(outputs.get(0).value);
        assertThat(first.getFeatureName()).isEqualTo("wc_utilization_ratio");
        assertThat(first.getEntityType()).isEqualTo("FACILITY");
        assertThat(Double.parseDouble(first.getValueNumeric())).isEqualTo(0.8);

        // Outstanding rises -> ratio recomputed against the same limit.
        inputTopic.pipeInput("fac-1", outstandingChangedJson("fac-1", 95000.0), base.plusSeconds(120));
        var secondOutputs = outputTopic.readKeyValuesToList();
        assertThat(secondOutputs).hasSize(1);
        JsonFeatureValue second = parse(secondOutputs.get(0).value);
        assertThat(Double.parseDouble(second.getValueNumeric())).isEqualTo(0.95);
    }

    @Test
    void computesUtilizationIndependentlyPerFacility() {
        inputTopic.pipeInput("fac-a", limitChangedJson("fac-a", 100000.0), Instant.now());
        inputTopic.pipeInput("fac-a", outstandingChangedJson("fac-a", 50000.0), Instant.now());
        inputTopic.pipeInput("fac-b", limitChangedJson("fac-b", 200000.0), Instant.now());
        inputTopic.pipeInput("fac-b", outstandingChangedJson("fac-b", 180000.0), Instant.now());

        var outputs = outputTopic.readKeyValuesToList();

        KeyValue<String, String> lastForA =
                outputs.stream().filter(kv -> "fac-a".equals(kv.key)).reduce((a, b) -> b).orElseThrow();
        KeyValue<String, String> lastForB =
                outputs.stream().filter(kv -> "fac-b".equals(kv.key)).reduce((a, b) -> b).orElseThrow();

        assertThat(Double.parseDouble(parse(lastForA.value).getValueNumeric())).isEqualTo(0.5);
        assertThat(Double.parseDouble(parse(lastForB.value).getValueNumeric())).isEqualTo(0.9);
    }

    @Test
    void malformedNonNumericSideIsSkippedRatherThanCrashingTheJoin() {
        // Roadmap 3.6: a malformed currentLimit/currentOutstanding must not crash the KTable-KTable
        // join's ValueJoiner (which would crash-loop the stream thread on the record forever); the
        // join must instead emit nothing for that pairing, exactly as if the value hadn't arrived.
        inputTopic.pipeInput(
                "fac-malformed", malformedLimitChangedJson("fac-malformed"), Instant.now());
        inputTopic.pipeInput(
                "fac-malformed", outstandingChangedJson("fac-malformed", 40000.0), Instant.now());
        assertThat(outputTopic.isEmpty()).isTrue();

        // A subsequent valid limit for the same facility must still join correctly.
        inputTopic.pipeInput("fac-malformed", limitChangedJson("fac-malformed", 100000.0), Instant.now());
        var outputs = outputTopic.readKeyValuesToList();
        assertThat(outputs).hasSize(1);
        assertThat(Double.parseDouble(parse(outputs.get(0).value).getValueNumeric())).isEqualTo(0.4);
    }

    private static String malformedLimitChangedJson(String facilityId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("facilityId", facilityId);
        data.put("counterpartyId", "cp-1");
        data.put("currentLimit", "not-a-number");
        data.put("currency", "USD");
        data.put("asOfDate", "2026-09-24");
        return toJson(
                new JsonEventEnvelope(
                        UUID.randomUUID().toString(),
                        "facility.limit.changed",
                        Instant.now().toString(),
                        "FACILITY",
                        facilityId,
                        data));
    }

    private static String limitChangedJson(String facilityId, double currentLimit) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("facilityId", facilityId);
        data.put("counterpartyId", "cp-1");
        data.put("currentLimit", currentLimit);
        data.put("currency", "USD");
        data.put("asOfDate", "2026-09-24");
        return toJson(
                new JsonEventEnvelope(
                        UUID.randomUUID().toString(),
                        "facility.limit.changed",
                        Instant.now().toString(),
                        "FACILITY",
                        facilityId,
                        data));
    }

    private static String outstandingChangedJson(String facilityId, double currentOutstanding) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("facilityId", facilityId);
        data.put("counterpartyId", "cp-1");
        data.put("currentOutstanding", currentOutstanding);
        data.put("currency", "USD");
        data.put("asOfDate", "2026-09-24");
        return toJson(
                new JsonEventEnvelope(
                        UUID.randomUUID().toString(),
                        "facility.outstanding.changed",
                        Instant.now().toString(),
                        "FACILITY",
                        facilityId,
                        data));
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
