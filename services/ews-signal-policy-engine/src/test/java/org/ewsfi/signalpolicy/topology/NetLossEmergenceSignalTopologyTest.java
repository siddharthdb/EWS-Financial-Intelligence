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
 * Tests {@link NetLossEmergenceSignalTopology}'s policy evaluation in isolation using
 * {@link TopologyTestDriver} -- no broker required. Proves NET_LOSS_EMERGENCE fires exactly on a
 * genuine profit(&gt;=0)-to-loss(&lt;0) transition between two <em>known</em> consecutive
 * {@code net_income} observations, and does not fire when already in a loss that deepens, when
 * staying profitable, or -- distinguishing this policy's deliberately conservative design from
 * {@code DpdSignalPolicyLoader}'s DPD_EMERGED -- on a company's very first tracked observation
 * already being a loss (no known previous state to transition from).
 */
class NetLossEmergenceSignalTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "net-loss-emergence-signal-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new NetLossEmergenceSignalTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        NetLossEmergenceSignalTopology.FEATURE_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        NetLossEmergenceSignalTopology.SIGNAL_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void firesOnAGenuineProfitToLossTransition() {
        pipeNetIncome("0000320193", 5_000_000.0);
        pipeNetIncome("0000320193", -1_000_000.0);

        List<String> outputs = outputTopic.readValuesToList();

        assertThat(outputs).hasSize(1);
        JsonSignalDetected signal = parse(outputs.get(0));
        assertThat(signal.getSignalType()).isEqualTo("NET_LOSS_EMERGENCE");
        assertThat(signal.getEntityType()).isEqualTo("COUNTERPARTY");
        assertThat(signal.getEntityId()).isEqualTo("0000320193");
        assertThat(signal.getStatus()).isEqualTo("PROPOSED");
    }

    @Test
    void firesOnATransitionFromBreakevenToLoss() {
        pipeNetIncome("0000320194", 0.0);
        pipeNetIncome("0000320194", -500.0);

        assertThat(outputTopic.readValuesToList()).hasSize(1);
    }

    @Test
    void doesNotFireWhenAlreadyInALossThatDeepens() {
        pipeNetIncome("0000320195", -1_000_000.0);
        pipeNetIncome("0000320195", -2_000_000.0);

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void doesNotFireWhenStayingProfitable() {
        pipeNetIncome("0000320196", 1_000_000.0);
        pipeNetIncome("0000320196", 1_500_000.0);

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void doesNotFireOnAFirstEverObservationThatIsAlreadyALoss() {
        // No known previous observation -- this platform hasn't witnessed a transition, only a
        // starting state, so NET_LOSS_EMERGENCE must not claim one (unlike DPD_EMERGED, which
        // treats an unknown previous DPD as an implicit zero baseline).
        pipeNetIncome("0000320197", -3_000_000.0);

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    private void pipeNetIncome(String cik, double netIncome) {
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        "FD-NET-INCOME-001",
                        "net_income",
                        "1.0",
                        "COUNTERPARTY",
                        cik,
                        "VALUE",
                        "DECIMAL",
                        String.valueOf(netIncome),
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
