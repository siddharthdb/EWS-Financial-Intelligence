package org.ewsfi.platform.outbox;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration entry point for services depending on {@code ews-platform-outbox-starter}.
 * Registered via {@code src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * <p>Declares its own {@code KafkaTemplate<String, String>} bean (String key/value serializers)
 * rather than relying on Spring Boot's own {@code KafkaAutoConfiguration}, whose default template
 * is typed {@code KafkaTemplate<Object, Object>} and would not satisfy the
 * {@code KafkaTemplate<String, String>} dependency {@link OutboxPublisherWorker} requires --
 * Spring's generic-aware autowiring does not treat {@code <Object, Object>} as assignable to a
 * required {@code <String, String>} type, so both beans can coexist without ambiguity.
 */
@AutoConfiguration
@EnableScheduling
@EntityScan(basePackageClasses = OutboxEvent.class)
@EnableJpaRepositories(basePackageClasses = OutboxEventRepository.class)
public class EwsOutboxAutoConfiguration {

    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> producerProps = kafkaProperties.buildProducerProperties(null);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        ProducerFactory<String, String> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        return new KafkaTemplate<>(producerFactory);
    }
}
