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
 * Tests {@link DpdFeatureTopology}'s logic in isolation using {@link TopologyTestDriver} -- no
 * broker required. Proves {@code current_dpd} always reflects only the latest observed value per
 * facility, not a windowed aggregate like {@code returned_payment_count_30d}.
 */
class DpdFeatureTopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "dpd-feature-topology-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        StreamsBuilder builder = new StreamsBuilder();
        new DpdFeatureTopology().build(builder);
        Topology topology = builder.build();

        driver = new TopologyTestDriver(topology, props);
        inputTopic =
                driver.createInputTopic(
                        DpdFeatureTopology.CANONICAL_TOPIC,
                        Serdes.String().serializer(),
                        Serdes.String().serializer());
        outputTopic =
                driver.createOutputTopic(
                        DpdFeatureTopology.FEATURE_TOPIC,
                        Serdes.String().deserializer(),
                        Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void currentDpdReflectsOnlyTheLatestObservedValuePerFacility() {
        Instant base = Instant.parse("2026-09-01T00:00:00Z");

        inputTopic.pipeInput("fac-1", dpdChangedJson("fac-1", null, 0), base);
        inputTopic.pipeInput("fac-1", dpdChangedJson("fac-1", 0, 5), base.plusSeconds(60));
        inputTopic.pipeInput("fac-1", dpdChangedJson("fac-1", 5, 12), base.plusSeconds(120));

        List<KeyValue<String, String>> outputs = outputTopic.readKeyValuesToList();

        // A windowed count would report 3; the latest-value KTable must report only the most
        // recent currentDpd (12), proving this is not a SlidingWindows aggregate.
        KeyValue<String, String> last = outputs.get(outputs.size() - 1);
        JsonFeatureValue latest = parse(last.value);

        assertThat(last.key).isEqualTo("fac-1");
        assertThat(latest.getFeatureName()).isEqualTo("current_dpd");
        assertThat(latest.getEntityType()).isEqualTo("FACILITY");
        assertThat(latest.getValueNumeric()).isEqualTo("12");
        assertThat(outputs).hasSize(3);
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

    private static String dpdChangedJson(String facilityId, Integer previousDpd, int currentDpd) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("facilityId", facilityId);
        data.put("counterpartyId", "cp-1");
        data.put("previousDpd", previousDpd);
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
