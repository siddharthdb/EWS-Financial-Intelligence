package org.ewsfi.featureprocessor.topology;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Tests {@link FilingDelayFeatureTopology}'s stateless per-event delay computation in isolation
 * using {@link TopologyTestDriver} -- no broker required.
 */
class FilingDelayFeatureTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "filing-delay-feature-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new FilingDelayFeatureTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        FilingDelayFeatureTopology.CANONICAL_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        FilingDelayFeatureTopology.FEATURE_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void computesTheDelayBetweenReportDateAndFilingDate() {
        inputTopic.pipeInput(
                "0000320193",
                statementReceivedJson("0000320193", "2026-06-15", "2026-03-31"),
                Instant.now());

        var outputs = outputTopic.readKeyValuesToList();

        assertThat(outputs).hasSize(1);
        JsonFeatureValue featureValue = parse(outputs.get(0).value);
        assertThat(featureValue.getFeatureName()).isEqualTo("financial_statement_filing_delay_days");
        assertThat(featureValue.getEntityType()).isEqualTo("COUNTERPARTY");
        assertThat(featureValue.getEntityId()).isEqualTo("0000320193");
        // 2026-03-31 -> 2026-06-15 is 76 days.
        assertThat(featureValue.getValueNumeric()).isEqualTo("76");
    }

    @Test
    void skipsFilingsWithNoReportDate() {
        inputTopic.pipeInput(
                "0000320193", statementReceivedJson("0000320193", "2026-06-15", ""), Instant.now());

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void unrelatedEventTypesProduceNoOutput() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("something", "else");
        JsonEventEnvelope envelope =
                new JsonEventEnvelope(
                        "evt-1", "financial.statement.validated", Instant.now().toString(), "COUNTERPARTY",
                        "0000320193", data);

        inputTopic.pipeInput("0000320193", toJson(envelope), Instant.now());

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    private static String statementReceivedJson(String cik, String filingDate, String reportDate) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cik", cik);
        data.put("companyName", "Test Filer Inc.");
        data.put("form", "10-Q");
        data.put("filingDate", filingDate);
        data.put("reportDate", reportDate);
        data.put("accessionNumber", "0000320193-26-000005");
        data.put("primaryDocumentUrl", "https://www.sec.gov/Archives/edgar/data/320193/x/doc.htm");
        JsonEventEnvelope envelope =
                new JsonEventEnvelope(
                        UUID.randomUUID().toString(),
                        "financial.statement.received",
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
