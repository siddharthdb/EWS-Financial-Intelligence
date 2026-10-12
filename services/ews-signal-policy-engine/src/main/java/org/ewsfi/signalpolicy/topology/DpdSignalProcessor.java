package org.ewsfi.signalpolicy.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.ewsfi.contracts.interim.JsonSignalDetected;
import org.ewsfi.signalpolicy.policy.DpdSignalPolicy;

/**
 * Proposes {@code DPD_EMERGED} or {@code DPD_WORSENING} from successive {@code current_dpd} feature
 * values. The last accepted DPD is kept in {@link DpdSignalPolicy#LAST_DPD_STORE}. The first value
 * for a facility falls back to {@link JsonFeatureValue#getPriorValueNumeric()}, which the feature
 * processor copies from {@code previousDpd} on {@code obligation.dpd.changed}.
 */
public class DpdSignalProcessor implements Processor<String, JsonFeatureValue, String, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProcessorContext<String, String> context;
    private KeyValueStore<String, Integer> lastDpd;

    @Override
    public void init(ProcessorContext<String, String> context) {
        this.context = context;
        this.lastDpd = context.getStateStore(DpdSignalPolicy.LAST_DPD_STORE);
    }

    @Override
    public void process(Record<String, JsonFeatureValue> record) {
        JsonFeatureValue feature = record.value();
        if (feature == null || feature.getValueNumeric() == null) {
            return;
        }
        int current = Integer.parseInt(feature.getValueNumeric());
        Integer stored = lastDpd.get(record.key());
        Integer previous = stored != null ? stored : parsePrior(feature.getPriorValueNumeric());
        String signalType = DpdSignalPolicy.classify(previous, current);
        lastDpd.put(record.key(), current);
        if (signalType == null) {
            return;
        }
        context.forward(record.withValue(toSignalJson(feature, signalType, current)));
    }

    @Override
    public void close() {
        // The state store is owned by Kafka Streams.
    }

    private String toSignalJson(JsonFeatureValue feature, String signalType, int currentDpd) {
        Instant now = Instant.now();
        String signalId =
                UUID.nameUUIDFromBytes(
                                (signalType + ":" + feature.getFeatureValueId()).getBytes(StandardCharsets.UTF_8))
                        .toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        signalType,
                        DpdSignalPolicy.SEMANTIC_SCOPE,
                        feature.getEntityType(),
                        feature.getEntityId(),
                        "PROPOSED",
                        DpdSignalPolicy.severity(currentDpd),
                        0.9,
                        DpdSignalPolicy.materiality(currentDpd),
                        now.toString(),
                        now.toString(),
                        feature.getKnowledgeTime(),
                        DpdSignalPolicy.policyId(signalType),
                        DpdSignalPolicy.POLICY_VERSION,
                        feature.getQualityState() != null ? feature.getQualityState() : "COMPLETE",
                        List.of(feature.getFeatureValueId()));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }

    private static Integer parsePrior(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Integer.valueOf(raw);
    }
}
