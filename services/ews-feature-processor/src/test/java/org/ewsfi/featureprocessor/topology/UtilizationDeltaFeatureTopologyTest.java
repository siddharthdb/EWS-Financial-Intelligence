package org.ewsfi.featureprocessor.topology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
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
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link UtilizationDeltaFeatureTopology}'s statistical-baseline logic in isolation using
 * {@link TopologyTestDriver} -- no broker required. Proves {@code wc_utilization_delta_30d}
 * reflects the current observation's deviation from its own rolling 30-day mean, not a fixed or
 * naive comparison.
 */
class UtilizationDeltaFeatureTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "utilization-delta-feature-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new UtilizationDeltaFeatureTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        UtilizationDeltaFeatureTopology.FEATURE_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        UtilizationDeltaFeatureTopology.FEATURE_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void deltaReflectsDeviationFromTheRollingMeanNotJustTheLatestValue() {
        Instant base = Instant.parse("2026-09-01T00:00:00Z");

        // Baseline around 0.5, then a spike to 0.9.
        pipeUtilizationRatio("fac-1", 0.50, base);
        pipeUtilizationRatio("fac-1", 0.50, base.plusSeconds(60));
        pipeUtilizationRatio("fac-1", 0.90, base.plusSeconds(120));

        List<KeyValue<String, String>> outputs = readDeltaOutputs();

        // Mean of {0.50, 0.50, 0.90} = 0.6333..; delta for the last observation = 0.90 - 0.6333 = 0.2667.
        JsonFeatureValue last = parse(outputs.get(outputs.size() - 1).value);
        assertThat(last.getFeatureName()).isEqualTo("wc_utilization_delta_30d");
        assertThat(Double.parseDouble(last.getValueNumeric())).isCloseTo(0.2667, within(0.001));
    }

    @Test
    void aSteadyUtilizationProducesADeltaNearZero() {
        Instant base = Instant.parse("2026-09-01T00:00:00Z");

        pipeUtilizationRatio("fac-2", 0.60, base);
        pipeUtilizationRatio("fac-2", 0.60, base.plusSeconds(60));
        pipeUtilizationRatio("fac-2", 0.60, base.plusSeconds(120));

        List<KeyValue<String, String>> outputs = readDeltaOutputs();
        JsonFeatureValue last = parse(outputs.get(outputs.size() - 1).value);

        assertThat(Double.parseDouble(last.getValueNumeric())).isCloseTo(0.0, within(0.001));
    }

    @Test
    void unrelatedFeatureNamesProduceNoOutput() {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-CURRENT-DPD-001",
                        "current_dpd",
                        "1.0",
                        "FACILITY",
                        "fac-3",
                        "VALUE",
                        "INTEGER",
                        "5",
                        Instant.now().toString(),
                        Instant.now().toString(),
                        null,
                        null,
                        "COMPLETE",
                        "1.0");
        inputTopic.pipeInput("fac-3", toJson(featureValue), Instant.now());

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void malformedNonNumericValueLeavesBaselineUnchangedRatherThanCrashingTheTopology() {
        // Roadmap 3.6: a malformed valueNumeric inside the stateful .aggregate() must leave the
        // baseline state unchanged, not throw (which would crash-loop the stream thread forever).
        Instant base = Instant.parse("2026-09-01T00:00:00Z");
        pipeUtilizationRatio("fac-malformed", 0.60, base);
        readDeltaOutputs(); // drain the output from the first valid observation

        JsonFeatureValue malformed =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-WC-UTILIZATION-RATIO-001",
                        "wc_utilization_ratio",
                        "1.0",
                        "FACILITY",
                        "fac-malformed",
                        "VALUE",
                        "DECIMAL",
                        "not-a-number",
                        base.plusSeconds(30).toString(),
                        base.plusSeconds(30).toString(),
                        null,
                        null,
                        "COMPLETE",
                        "1.0");
        inputTopic.pipeInput("fac-malformed", toJson(malformed), base.plusSeconds(30));

        // SlidingWindows still opens new windows at this record's timestamp (folding in the
        // surrounding valid records), so output is not necessarily suppressed entirely -- but every
        // emitted delta must still reflect only the genuine 0.60 observation's baseline (mean 0.60,
        // latest 0.60 -> delta 0.0), proving the malformed value itself never entered the running
        // sum/count rather than corrupting it.
        for (KeyValue<String, String> kv : readDeltaOutputs()) {
            assertThat(Double.parseDouble(parse(kv.value).getValueNumeric())).isCloseTo(0.0, within(0.001));
        }

        // The next valid observation must still compute a correct delta against the pre-malformed
        // baseline (just the single 0.60 observation), proving the malformed record was skipped
        // rather than corrupting the running sum/count.
        pipeUtilizationRatio("fac-malformed", 0.60, base.plusSeconds(60));
        List<KeyValue<String, String>> outputs = readDeltaOutputs();
        JsonFeatureValue last = parse(outputs.get(outputs.size() - 1).value);
        assertThat(Double.parseDouble(last.getValueNumeric())).isCloseTo(0.0, within(0.001));
    }

    private List<KeyValue<String, String>> readDeltaOutputs() {
        return outputTopic.readKeyValuesToList().stream()
                .filter(kv -> "wc_utilization_delta_30d".equals(parse(kv.value).getFeatureName()))
                .toList();
    }

    private void pipeUtilizationRatio(String facilityId, double ratio, Instant timestamp) {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-WC-UTILIZATION-RATIO-001",
                        "wc_utilization_ratio",
                        "1.0",
                        "FACILITY",
                        facilityId,
                        "VALUE",
                        "DECIMAL",
                        String.valueOf(ratio),
                        timestamp.toString(),
                        timestamp.toString(),
                        null,
                        null,
                        "COMPLETE",
                        "1.0");
        inputTopic.pipeInput(facilityId, toJson(featureValue), timestamp);
    }

    private static String toJson(JsonFeatureValue featureValue) {
        try {
            return MAPPER.writeValueAsString(featureValue);
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
