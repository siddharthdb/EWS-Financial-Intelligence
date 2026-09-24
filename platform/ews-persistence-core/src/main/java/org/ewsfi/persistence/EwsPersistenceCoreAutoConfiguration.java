package org.ewsfi.persistence;

import org.ewsfi.persistence.envelope.CanonicalEventEnvelope;
import org.ewsfi.persistence.envelope.CanonicalEventEnvelopeRepository;
import org.ewsfi.persistence.feature.FeatureValue;
import org.ewsfi.persistence.feature.FeatureValueRepository;
import org.ewsfi.persistence.signal.SignalInstance;
import org.ewsfi.persistence.signal.SignalInstanceRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Auto-configuration entry point for services depending on {@code ews-persistence-core}.
 * Registered via
 * {@code src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@EntityScan(
        basePackageClasses = {
            CanonicalEventEnvelope.class,
            FeatureValue.class,
            SignalInstance.class
        })
@EnableJpaRepositories(
        basePackageClasses = {
            CanonicalEventEnvelopeRepository.class,
            FeatureValueRepository.class,
            SignalInstanceRepository.class
        })
public class EwsPersistenceCoreAutoConfiguration {
}
