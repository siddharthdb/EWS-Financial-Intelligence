package org.ewsfi.featureprocessor.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.ewsfi.avro.AvroPayloadCodec;
import org.ewsfi.avro.AvroStreams;

/**
 * Computes {@code wc_utilization_ratio} and {@code wc_available_headroom} from the latest
 * sanctioned limit and eligible outstanding on {@code ews.canonical.facility}. Applicable capacity
 * in this slice is the sanctioned limit ({@code SANCTIONED_LIMIT}). A non-positive capacity, or a
 * currency mismatch, is {@code INVALID} and is not treated as zero utilization.
 */
public class UtilizationFeatureBranch {

    static final String FACILITY_TOPIC = "ews.canonical.facility";
    static final String LIMIT_EVENT = "facility.limit.changed";
    static final String OUTSTANDING_EVENT = "facility.outstanding.changed";
    static final String RATIO = "wc_utilization_ratio";
    static final String HEADROOM = "wc_available_headroom";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AvroPayloadCodec avroPayloadCodec;

    public UtilizationFeatureBranch(AvroPayloadCodec avroPayloadCodec) {
        this.avroPayloadCodec = avroPayloadCodec;
    }

    public void attach(StreamsBuilder builder) {
        KStream<String, String> facility =
                builder.stream(FACILITY_TOPIC, Consumed.with(Serdes.String(), Serdes.ByteArray()))
                        .process(AvroStreams.decode(avroPayloadCodec));

        facility.filter((key, value) -> LIMIT_EVENT.equals(eventType(value)))
                .toTable(table("facility-sanctioned-limit"))
                .join(
                        facility.filter((key, value) -> OUTSTANDING_EVENT.equals(eventType(value)))
                                .toTable(table("facility-eligible-outstanding")),
                        this::combine)
                .toStream()
                .flatMap(this::split)
                .process(AvroStreams.encode(avroPayloadCodec, "feature.value.updated"))
                .to(FeatureProcessorTopology.FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.ByteArray()));
    }

    private String combine(String limitJson, String outstandingJson) {
        try {
            JsonNode limit = objectMapper.readTree(limitJson);
            JsonNode outstanding = objectMapper.readTree(outstandingJson);
            JsonNode limitData = limit.get("data");
            JsonNode outstandingData = outstanding.get("data");
            String facilityId = limit.get("entityId").asText();
            boolean currencyMatches =
                    limitData.get("currency").asText().equals(outstandingData.get("currency").asText());
            BigDecimal capacity = new BigDecimal(limitData.get("sanctionedLimit").asText());
            BigDecimal exposure = new BigDecimal(outstandingData.get("eligibleOutstanding").asText());
            boolean usable = currencyMatches && capacity.signum() > 0;
            String identity = limit.get("eventId").asText() + "|" + outstanding.get("eventId").asText();
            String time = outstanding.get("eventTime").asText();
            ArrayNode features = objectMapper.createArrayNode();
            if (!usable) {
                features.add(feature(identity, facilityId, RATIO, "INVALID", null, "DECIMAL", "INSUFFICIENT", time));
                features.add(feature(identity, facilityId, HEADROOM, "INVALID", null, "DECIMAL", "INSUFFICIENT", time));
            } else {
                BigDecimal ratio = exposure.divide(capacity, 6, RoundingMode.HALF_UP);
                BigDecimal headroom = capacity.subtract(exposure);
                features.add(feature(identity, facilityId, RATIO, "VALUE", ratio.toPlainString(), "DECIMAL", "COMPLETE", time));
                features.add(
                        feature(
                                identity,
                                facilityId,
                                HEADROOM,
                                headroom.signum() == 0 ? "ZERO" : "VALUE",
                                headroom.toPlainString(),
                                "DECIMAL",
                                "COMPLETE",
                                time));
            }
            return objectMapper.writeValueAsString(features);
        } catch (Exception e) {
            throw new IllegalStateException("Could not combine facility capacity and outstanding", e);
        }
    }

    private List<KeyValue<String, String>> split(String facilityId, String combinedJson) {
        try {
            ArrayNode features = (ArrayNode) objectMapper.readTree(combinedJson);
            return List.of(
                    KeyValue.pair(facilityId, features.get(0).toString()),
                    KeyValue.pair(facilityId, features.get(1).toString()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not split utilization features", e);
        }
    }

    private ObjectNode feature(
            String identity,
            String facilityId,
            String name,
            String state,
            String numeric,
            String valueType,
            String quality,
            String time) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(
                "featureValueId",
                UUID.nameUUIDFromBytes((name + "|" + identity).getBytes(StandardCharsets.UTF_8)).toString());
        node.put("definitionId", "FD-" + name.toUpperCase().replace('_', '-') + "-001");
        node.put("featureName", name);
        node.put("definitionVersion", "1.0");
        node.put("entityType", "FACILITY");
        node.put("entityId", facilityId);
        node.put("state", state);
        node.put("valueType", valueType);
        if (numeric == null) {
            node.putNull("valueNumeric");
        } else {
            node.put("valueNumeric", numeric);
        }
        node.put("knowledgeTime", time);
        node.put("calculatedAt", time);
        node.put("windowStart", time);
        node.put("windowEnd", time);
        node.put("qualityState", quality);
        node.put("lineageTransformationVersion", "1.0");
        return node;
    }

    private static Materialized<String, String, KeyValueStore<Bytes, byte[]>> table(String name) {
        return Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as(name)
                .withKeySerde(Serdes.String())
                .withValueSerde(Serdes.String());
    }

    private String eventType(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.path("eventType").asText();
        } catch (Exception e) {
            return "";
        }
    }
}
