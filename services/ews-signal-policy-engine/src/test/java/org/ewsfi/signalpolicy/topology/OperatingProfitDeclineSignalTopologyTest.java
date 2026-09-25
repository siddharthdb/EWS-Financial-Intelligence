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
 * Tests {@link OperatingProfitDeclineSignalTopology}'s policy evaluation in isolation using
 * {@link TopologyTestDriver} -- no broker required. Proves OPERATING_PROFIT_MATERIAL_DECLINE fires
 * exactly on a material (&gt;=20%) relative decrease in {@code operating_income}, and does not fire
 * on a smaller decrease, an increase, or when the previous observation was a loss.
 */
class OperatingProfitDeclineSignalTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "operating-profit-decline-signal-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new OperatingProfitDeclineSignalTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        OperatingProfitDeclineSignalTopology.FEATURE_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        OperatingProfitDeclineSignalTopology.SIGNAL_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void firesOnAMaterialRelativeDeclineInOperatingIncome() {
        pipeOperatingIncome("0000320193", 100_000_000.0);
        pipeOperatingIncome("0000320193", 70_000_000.0); // -30% relative, above the 20% threshold

        List<String> outputs = outputTopic.readValuesToList();

        assertThat(outputs).hasSize(1);
        JsonSignalDetected signal = parse(outputs.get(0));
        assertThat(signal.getSignalType()).isEqualTo("OPERATING_PROFIT_MATERIAL_DECLINE");
        assertThat(signal.getEntityType()).isEqualTo("COUNTERPARTY");
        assertThat(signal.getEntityId()).isEqualTo("0000320193");
        assertThat(signal.getStatus()).isEqualTo("PROPOSED");
    }

    @Test
    void doesNotFireOnASmallRelativeDeclineBelowTheThreshold() {
        pipeOperatingIncome("0000320194", 100_000_000.0);
        pipeOperatingIncome("0000320194", 90_000_000.0); // -10% relative, below the 20% threshold

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void doesNotFireOnAnIncreaseInOperatingIncome() {
        pipeOperatingIncome("0000320195", 50_000_000.0);
        pipeOperatingIncome("0000320195", 100_000_000.0);

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void doesNotFireWhenThePreviousObservationWasALoss() {
        pipeOperatingIncome("0000320196", -10_000_000.0);
        pipeOperatingIncome("0000320196", -50_000_000.0); // worse loss, but no positive baseline

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    private void pipeOperatingIncome(String cik, double operatingIncome) {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-OPERATING-INCOME-001",
                        "operating_income",
                        "1.0",
                        "COUNTERPARTY",
                        cik,
                        "VALUE",
                        "DECIMAL",
                        String.valueOf(operatingIncome),
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
