package org.ewsfi.signalpolicy.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.ewsfi.avro.AvroPayloadCodec;
import org.ewsfi.avro.AvroStreams;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.ewsfi.contracts.interim.JsonSignalDetected;
import org.ewsfi.signalpolicy.policy.DpdSignalPolicy;
import org.ewsfi.signalpolicy.policy.SignalPolicyLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology evaluating rule-method policies against
 * {@code ews.derived.feature} and producing {@code signal.detected} events on
 * {@code ews.derived.signal}. P03 {@code REPEATED_PAYMENT_RETURN} is stateless
 * ({@link SignalPolicyLoader}). P01 {@code DPD_EMERGED} and P02 {@code DPD_WORSENING} are stateful
 * ({@link DpdSignalProcessor}) so an unchanged DPD does not open another proposed signal.
 *
 * <p>The payment-return branch emits a signal on every window where the threshold is met.
 * Deduplication of repeated {@code REPEATED_PAYMENT_RETURN} detections is still open. DPD signals
 * suppress unchanged and within-band increases via {@link DpdSignalPolicy}. Each signal id is
 * deterministic from the triggering feature value so at-least-once redelivery does not create a
 * second proposed row.
 */
@Component
public class SignalPolicyTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String SIGNAL_TOPIC = "ews.derived.signal";
    private static final String TARGET_FEATURE_NAME = "returned_payment_count_30d";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AvroPayloadCodec avroPayloadCodec;

    public SignalPolicyTopology(AvroPayloadCodec avroPayloadCodec) {
        this.avroPayloadCodec = avroPayloadCodec;
    }

    public KStream<String, byte[]> build(StreamsBuilder builder) {
        KStream<String, JsonFeatureValue> features =
                builder.stream(FEATURE_TOPIC, Consumed.with(Serdes.String(), Serdes.ByteArray()))
                        .process(AvroStreams.decode(avroPayloadCodec))
                        .mapValues(this::tryParse);

        KStream<String, byte[]> paymentSignals =
                features.filter((key, featureValue) -> matchesPaymentPolicy(featureValue))
                        .mapValues(this::toSignalDetectedJson)
                        .process(AvroStreams.encode(avroPayloadCodec, "signal.detected"));
        paymentSignals.to(SIGNAL_TOPIC, Produced.with(Serdes.String(), Serdes.ByteArray()));

        StoreBuilder<KeyValueStore<String, Integer>> lastDpd =
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(DpdSignalPolicy.LAST_DPD_STORE),
                        Serdes.String(),
                        Serdes.Integer());
        builder.addStateStore(lastDpd);
        features.filter((key, featureValue) -> isTrackableDpd(featureValue))
                .process(DpdSignalProcessor::new, DpdSignalPolicy.LAST_DPD_STORE)
                .process(AvroStreams.encode(avroPayloadCodec, "signal.detected"))
                .to(SIGNAL_TOPIC, Produced.with(Serdes.String(), Serdes.ByteArray()));
        return paymentSignals;
    }

    private JsonFeatureValue tryParse(String json) {
        try {
            return objectMapper.readValue(json, JsonFeatureValue.class);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isTrackableDpd(JsonFeatureValue featureValue) {
        return featureValue != null
                && "current_dpd".equals(featureValue.getFeatureName())
                && ("VALUE".equals(featureValue.getState()) || "ZERO".equals(featureValue.getState()))
                && featureValue.getValueNumeric() != null;
    }

    private boolean matchesPaymentPolicy(JsonFeatureValue featureValue) {
        if (featureValue == null || !TARGET_FEATURE_NAME.equals(featureValue.getFeatureName())) {
            return false;
        }
        if (featureValue.getValueNumeric() == null) {
            return false;
        }
        long count = Long.parseLong(featureValue.getValueNumeric());
        return SignalPolicyLoader.evaluate(count);
    }

    private String toSignalDetectedJson(JsonFeatureValue featureValue) {
        Instant now = Instant.now();
        String signalId =
                UUID.nameUUIDFromBytes(featureValue.getFeatureValueId().getBytes()).toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        SignalPolicyLoader.SIGNAL_TYPE,
                        SignalPolicyLoader.SEMANTIC_SCOPE,
                        featureValue.getEntityType(),
                        featureValue.getEntityId(),
                        "PROPOSED",
                        "MEDIUM",
                        0.8,
                        "MEDIUM",
                        now.toString(),
                        now.toString(),
                        featureValue.getKnowledgeTime(),
                        SignalPolicyLoader.POLICY_ID,
                        SignalPolicyLoader.POLICY_VERSION,
                        featureValue.getQualityState() != null ? featureValue.getQualityState() : "COMPLETE",
                        List.of(featureValue.getFeatureValueId()));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }
}
