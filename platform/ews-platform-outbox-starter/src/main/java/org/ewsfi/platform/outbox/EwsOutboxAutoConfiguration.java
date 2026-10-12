package org.ewsfi.platform.outbox;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
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
 * <p>Declares its own {@code KafkaTemplate<String, byte[]>} bean. The value is already a Confluent
 * Avro frame produced by {@code AvroPayloadCodec}, so the template must not apply a second
 * serializer. Spring Boot's default template is typed {@code KafkaTemplate<Object, Object>} and
 * would not satisfy this dependency.
 */
@AutoConfiguration
@EnableScheduling
@EntityScan(basePackageClasses = OutboxEvent.class)
@EnableJpaRepositories(basePackageClasses = OutboxEventRepository.class)
public class EwsOutboxAutoConfiguration {

    @Bean
    public KafkaTemplate<String, byte[]> outboxKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> producerProps = kafkaProperties.buildProducerProperties(null);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        ProducerFactory<String, byte[]> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        return new KafkaTemplate<>(producerFactory);
    }
}
