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
 * Tests {@link InventoryDaysDeteriorationSignalTopology}'s policy evaluation in isolation using
 * {@link TopologyTestDriver} -- no broker required. Proves INVENTORY_DAYS_DERIORATION fires exactly
 * on a material (&gt;=20%) relative increase in {@code inventory_days}, and does not fire on a
 * smaller increase or a decrease.
 */
class InventoryDaysDeteriorationSignalTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "inventory-days-deterioration-signal-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new InventoryDaysDeteriorationSignalTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        InventoryDaysDeteriorationSignalTopology.FEATURE_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        InventoryDaysDeteriorationSignalTopology.SIGNAL_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void firesOnAMaterialRelativeIncreaseInInventoryDays() {
        pipeInventoryDays("0000320193", 20.0);
        pipeInventoryDays("0000320193", 30.0); // +50% relative, above the 20% threshold

        List<String> outputs = outputTopic.readValuesToList();

        assertThat(outputs).hasSize(1);
        JsonSignalDetected signal = parse(outputs.get(0));
        assertThat(signal.getSignalType()).isEqualTo("INVENTORY_DAYS_DERIORATION");
        assertThat(signal.getEntityType()).isEqualTo("COUNTERPARTY");
        assertThat(signal.getEntityId()).isEqualTo("0000320193");
        assertThat(signal.getStatus()).isEqualTo("PROPOSED");
    }

    @Test
    void doesNotFireOnASmallRelativeIncreaseBelowTheThreshold() {
        pipeInventoryDays("0000320194", 20.0);
        pipeInventoryDays("0000320194", 22.0); // +10% relative, below the 20% threshold

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void doesNotFireOnADecreaseInInventoryDays() {
        pipeInventoryDays("0000320195", 30.0);
        pipeInventoryDays("0000320195", 18.0);

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    private void pipeInventoryDays(String cik, double days) {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-INVENTORY-DAYS-001",
                        "inventory_days",
                        "1.0",
                        "COUNTERPARTY",
                        cik,
                        "VALUE",
                        "DECIMAL",
                        String.valueOf(days),
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
