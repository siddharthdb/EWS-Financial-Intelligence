package org.ewsfi.signalpolicy.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
import org.ewsfi.signalpolicy.policy.UtilizationSignalPolicy;

/**
 * Proposes {@code UTILIZATION_HIGH} when utilization crosses the high threshold, and
 * {@code UTILIZATION_SPIKE} when it jumps by the configured delta from the previous ratio.
 */
public class UtilizationSignalProcessor implements Processor<String, JsonFeatureValue, String, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProcessorContext<String, String> context;
    private KeyValueStore<String, String> lastRatio;

    @Override
    public void init(ProcessorContext<String, String> context) {
        this.context = context;
        this.lastRatio = context.getStateStore(UtilizationSignalPolicy.LAST_RATIO_STORE);
    }

    @Override
    public void process(Record<String, JsonFeatureValue> record) {
        JsonFeatureValue feature = record.value();
        if (feature == null || feature.getValueNumeric() == null) {
            return;
        }
        BigDecimal current = new BigDecimal(feature.getValueNumeric());
        String stored = lastRatio.get(record.key());
        BigDecimal previous = stored == null ? null : new BigDecimal(stored);
        if (UtilizationSignalPolicy.crossesIntoHigh(previous, current)) {
            context.forward(record.withValue(toSignal(feature, UtilizationSignalPolicy.HIGH, current)));
        }
        if (UtilizationSignalPolicy.isSpike(previous, current)) {
            context.forward(record.withValue(toSignal(feature, UtilizationSignalPolicy.SPIKE, current)));
        }
        lastRatio.put(record.key(), current.toPlainString());
    }

    @Override
    public void close() {}

    private String toSignal(JsonFeatureValue feature, String signalType, BigDecimal current) {
        Instant now = Instant.now();
        String signalId =
                UUID.nameUUIDFromBytes(
                                (signalType + ":" + feature.getFeatureValueId()).getBytes(StandardCharsets.UTF_8))
                        .toString();
        boolean highBand = current.compareTo(UtilizationSignalPolicy.HIGH_THRESHOLD) >= 0;
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        signalType,
                        UtilizationSignalPolicy.SEMANTIC_SCOPE,
                        feature.getEntityType(),
                        feature.getEntityId(),
                        "PROPOSED",
                        highBand ? "HIGH" : "MEDIUM",
                        0.85,
                        highBand ? "HIGH" : "MEDIUM",
                        now.toString(),
                        now.toString(),
                        feature.getKnowledgeTime(),
                        UtilizationSignalPolicy.policyId(signalType),
                        UtilizationSignalPolicy.POLICY_VERSION,
                        feature.getQualityState() != null ? feature.getQualityState() : "COMPLETE",
                        List.of(feature.getFeatureValueId()));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize utilization signal", e);
        }
    }
}
