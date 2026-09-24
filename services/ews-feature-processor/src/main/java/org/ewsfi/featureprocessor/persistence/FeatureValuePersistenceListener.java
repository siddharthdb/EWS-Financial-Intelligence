package org.ewsfi.featureprocessor.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.ewsfi.persistence.feature.FeatureValue;
import org.ewsfi.persistence.feature.FeatureValueRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Persists each {@code feature.value.updated} event the Kafka Streams topology emits to
 * {@code ews.derived.feature} as a {@code feature_value} row, via a plain {@code @KafkaListener}
 * (spring-kafka) rather than inside the Streams topology itself, per
 * docs/architecture/07-build-log.md's 2026-09-24 feature-processor entry -- deliberately keeping
 * database writes out of the Streams processing thread and independently testable.
 *
 * <p>Deliberately does not catch exceptions itself: letting them propagate lets
 * {@link org.ewsfi.featureprocessor.config.KafkaListenerErrorHandlingConfig}'s container-level
 * error handler retry a transient failure (e.g. a momentary Postgres connection blip) instead of
 * silently dropping the feature value on the first attempt -- see that class's Javadoc for the bug
 * this fixed.
 */
@Component
public class FeatureValuePersistenceListener {

    private final FeatureValueRepository featureValueRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FeatureValuePersistenceListener(FeatureValueRepository featureValueRepository) {
        this.featureValueRepository = featureValueRepository;
    }

    @KafkaListener(topics = "ews.derived.feature", groupId = "ews-feature-processor-persistence")
    public void onFeatureValue(String payload) throws Exception {
        JsonFeatureValue json = objectMapper.readValue(payload, JsonFeatureValue.class);
        FeatureValue entity =
                new FeatureValue(
                        json.getFeatureValueId(),
                        json.getDefinitionId(),
                        json.getFeatureName(),
                        json.getDefinitionVersion(),
                        json.getEntityType(),
                        json.getEntityId(),
                        json.getState(),
                        json.getValueType(),
                        new BigDecimal(json.getValueNumeric()),
                        OffsetDateTime.parse(json.getKnowledgeTime()),
                        OffsetDateTime.parse(json.getCalculatedAt()),
                        OffsetDateTime.parse(json.getWindowStart()),
                        OffsetDateTime.parse(json.getWindowEnd()),
                        json.getQualityState(),
                        json.getLineageTransformationVersion());
        featureValueRepository.save(entity);
    }
}
