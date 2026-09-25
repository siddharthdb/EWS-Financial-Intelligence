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
 * Tests {@link OperatingCashFlowNegativeSignalTopology}'s policy evaluation in isolation using
 * {@link TopologyTestDriver} -- no broker required.
 */
class OperatingCashFlowNegativeSignalTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "operating-cash-flow-negative-signal-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new OperatingCashFlowNegativeSignalTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        OperatingCashFlowNegativeSignalTopology.FEATURE_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        OperatingCashFlowNegativeSignalTopology.SIGNAL_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void firesWhenOperatingCashFlowIsNegative() {
        pipeOperatingCashFlow("0000320193", -500_000.0);

        List<String> outputs = outputTopic.readValuesToList();

        assertThat(outputs).hasSize(1);
        JsonSignalDetected signal = parse(outputs.get(0));
        assertThat(signal.getSignalType()).isEqualTo("OPERATING_CASH_FLOW_NEGATIVE");
        assertThat(signal.getEntityType()).isEqualTo("COUNTERPARTY");
        assertThat(signal.getEntityId()).isEqualTo("0000320193");
        assertThat(signal.getStatus()).isEqualTo("PROPOSED");
    }

    @Test
    void doesNotFireWhenOperatingCashFlowIsPositive() {
        pipeOperatingCashFlow("0000320194", 500_000.0);

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void ignoresUnrelatedFeatureNames() {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-OPERATING-INCOME-001",
                        "operating_income",
                        "1.0",
                        "COUNTERPARTY",
                        "0000320195",
                        "VALUE",
                        "DECIMAL",
                        "-500000",
                        Instant.now().toString(),
                        Instant.now().toString(),
                        null,
                        null,
                        "COMPLETE",
                        "1.0");
        inputTopic.pipeInput("0000320195", toJson(featureValue));

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    private void pipeOperatingCashFlow(String cik, double value) {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-OPERATING-CASH-FLOW-001",
                        "operating_cash_flow",
                        "1.0",
                        "COUNTERPARTY",
                        cik,
                        "VALUE",
                        "DECIMAL",
                        String.valueOf(value),
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
