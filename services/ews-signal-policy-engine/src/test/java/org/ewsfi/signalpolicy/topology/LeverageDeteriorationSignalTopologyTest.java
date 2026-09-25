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
 * Tests {@link LeverageDeteriorationSignalTopology}'s policy evaluation in isolation using
 * {@link TopologyTestDriver} -- no broker required. Proves LEVERAGE_DERIORATION fires exactly on a
 * material (&gt;=20%) relative increase in {@code total_liabilities_to_equity}, and does not fire on
 * a smaller increase or a decrease.
 */
class LeverageDeteriorationSignalTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "leverage-deterioration-signal-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new LeverageDeteriorationSignalTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        LeverageDeteriorationSignalTopology.FEATURE_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        LeverageDeteriorationSignalTopology.SIGNAL_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void firesOnAMaterialRelativeIncreaseInLeverage() {
        pipeLeverageRatio("0000320193", 2.0);
        pipeLeverageRatio("0000320193", 2.5); // +25% relative, above the 20% threshold

        List<String> outputs = outputTopic.readValuesToList();

        assertThat(outputs).hasSize(1);
        JsonSignalDetected signal = parse(outputs.get(0));
        assertThat(signal.getSignalType()).isEqualTo("LEVERAGE_DERIORATION");
        assertThat(signal.getEntityType()).isEqualTo("COUNTERPARTY");
        assertThat(signal.getEntityId()).isEqualTo("0000320193");
        assertThat(signal.getStatus()).isEqualTo("PROPOSED");
    }

    @Test
    void doesNotFireOnASmallRelativeIncreaseBelowTheThreshold() {
        pipeLeverageRatio("0000320194", 2.0);
        pipeLeverageRatio("0000320194", 2.2); // +10% relative, below the 20% threshold

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void doesNotFireOnADecreaseInLeverage() {
        pipeLeverageRatio("0000320195", 3.0);
        pipeLeverageRatio("0000320195", 1.5);

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    private void pipeLeverageRatio(String cik, double ratio) {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-TOTAL-LIABILITIES-TO-EQUITY-001",
                        "total_liabilities_to_equity",
                        "1.0",
                        "COUNTERPARTY",
                        cik,
                        "VALUE",
                        "DECIMAL",
                        String.valueOf(ratio),
                        Instant.now().toString(),
                        Instant.now().toString(),
                        null,
                        null,
                        "COMPLETE",
                        "1.0");
        inputTopic.pipeInput(cik, toJson(featureValue));
    }

    private static String toJson(JsonFeatureValue featureValue) {
        try {
            return MAPPER.writeValueAsString(featureValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonSignalDetected parse(String json) {
        try {
            return MAPPER.readValue(json, JsonSignalDetected.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
