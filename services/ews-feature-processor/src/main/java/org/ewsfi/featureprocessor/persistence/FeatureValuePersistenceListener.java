package org.ewsfi.featureprocessor.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.ewsfi.persistence.feature.FeatureValue;
import org.ewsfi.persistence.feature.FeatureValueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Persists each {@code feature.value.updated} event the Kafka Streams topology emits to
 * {@code ews.derived.feature} as a {@code feature_value} row, via a plain {@code @KafkaListener}
 * (spring-kafka) rather than inside the Streams topology itself, per
 * docs/architecture/07-build-log.md's 2026-09-24 feature-processor entry -- deliberately keeping
 * database writes out of the Streams processing thread and independently testable.
 */
@Component
public class FeatureValuePersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(FeatureValuePersistenceListener.class);

    private final FeatureValueRepository featureValueRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FeatureValuePersistenceListener(FeatureValueRepository featureValueRepository) {
        this.featureValueRepository = featureValueRepository;
    }

    @KafkaListener(topics = "ews.derived.feature", groupId = "ews-feature-processor-persistence")
    public void onFeatureValue(String payload) {
        try {
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
        } catch (Exception e) {
            log.warn("Failed to persist feature value from payload {}: {}", payload, e.getMessage());
        }
    }
}
