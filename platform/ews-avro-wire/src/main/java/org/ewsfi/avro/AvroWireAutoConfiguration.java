package org.ewsfi.avro;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Exposes one {@link AvroPayloadCodec} for every service. The default URL is Apicurio's
 * Confluent-compatible endpoint in {@code docker-compose.yml}. Tests override it with
 * {@code mock://ews-phase1}.
 */
@AutoConfiguration
public class AvroWireAutoConfiguration {

    @Bean
    public AvroPayloadCodec avroPayloadCodec(
            @Value("${ews.schema.registry.url:http://localhost:8081/apis/ccompat/v7}") String registryUrl) {
        return new AvroPayloadCodec(registryUrl);
    }
}
