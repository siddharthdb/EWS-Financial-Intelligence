package org.ewsfi.featureprocessor.topology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
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
 * Tests {@link LeverageRatioFeatureTopology}'s stateless per-event ratio computation in isolation
 * using {@link TopologyTestDriver} -- no broker required.
 */
class LeverageRatioFeatureTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "leverage-ratio-feature-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new LeverageRatioFeatureTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        LeverageRatioFeatureTopology.CANONICAL_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        LeverageRatioFeatureTopology.FEATURE_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void computesLiabilitiesDividedByEquity() {
        inputTopic.pipeInput(
                "0000320193", validatedJson("0000320193", 275_746_000_000L, 107_520_000_000L), Instant.now());

        var outputs = outputTopic.readKeyValuesToList();

        assertThat(outputs).hasSize(1);
        JsonFeatureValue featureValue = parse(outputs.get(0).value);
        assertThat(featureValue.getFeatureName()).isEqualTo("total_liabilities_to_equity");
        assertThat(featureValue.getEntityType()).isEqualTo("COUNTERPARTY");
        assertThat(featureValue.getEntityId()).isEqualTo("0000320193");
        assertThat(Double.parseDouble(featureValue.getValueNumeric())).isCloseTo(2.5647, within(0.001));
    }

    @Test
    void nonPositiveEquityGuardrailSkipsRatherThanEmittingAMisleadingRatio() {
        inputTopic.pipeInput(
                "0000999999", validatedJson("0000999999", 500_000_000L, 0L), Instant.now());
        inputTopic.pipeInput(
                "0000999999", validatedJson("0000999999", 500_000_000L, -50_000_000L), Instant.now());

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void missingFactsProduceNoOutput() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cik", "0000320193");
        data.put("facts", Map.of("Assets", 383_266_000_000L)); // no Liabilities/StockholdersEquity
        JsonEventEnvelope envelope =
                new JsonEventEnvelope(
                        UUID.randomUUID().toString(),
                        "financial.statement.validated",
                        Instant.now().toString(),
                        "COUNTERPARTY",
                        "0000320193",
                        data);

        inputTopic.pipeInput("0000320193", toJson(envelope), Instant.now());

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void unrelatedEventTypesProduceNoOutput() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("something", "else");
        JsonEventEnvelope envelope =
                new JsonEventEnvelope(
                        "evt-1", "financial.statement.received", Instant.now().toString(), "COUNTERPARTY",
                        "0000320193", data);

        inputTopic.pipeInput("0000320193", toJson(envelope), Instant.now());

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    private static String validatedJson(String cik, long liabilities, long equity) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("Assets", liabilities + equity);
        facts.put("Liabilities", liabilities);
        facts.put("StockholdersEquity", equity);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cik", cik);
        data.put("companyName", "Test Filer Inc.");
        data.put("form", "10-Q");
        data.put("reportDate", "2026-06-27");
        data.put("accessionNumber", "0000320193-26-000020");
        data.put("facts", facts);

        JsonEventEnvelope envelope =
                new JsonEventEnvelope(
                        UUID.randomUUID().toString(),
                        "financial.statement.validated",
                        Instant.now().toString(),
                        "COUNTERPARTY",
                        cik,
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
