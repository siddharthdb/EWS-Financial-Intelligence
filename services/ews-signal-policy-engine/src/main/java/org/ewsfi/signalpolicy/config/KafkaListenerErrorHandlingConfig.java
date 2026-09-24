package org.ewsfi.signalpolicy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Retry-with-backoff error handling for this service's {@code @KafkaListener}s (roadmap item 3.6,
 * production hardening), mirroring {@code ews-feature-processor}'s
 * {@code KafkaListenerErrorHandlingConfig} -- see that class's Javadoc for the bug this pattern
 * fixes: without it, {@link org.ewsfi.signalpolicy.persistence.SignalInstancePersistenceListener}
 * caught every exception internally and only logged a warning, so a transient failure (e.g. a
 * momentary Postgres connection blip) silently dropped the signal instance forever, with Kafka's
 * offset still committed as if processing had succeeded.
 */
@Configuration
public class KafkaListenerErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerErrorHandlingConfig.class);

    @Bean
    public CommonErrorHandler kafkaListenerErrorHandler() {
        return new DefaultErrorHandler(
                (record, exception) ->
                        log.error(
                                "Permanently failed to process Kafka record from topic {} partition {}"
                                        + " offset {} after retries exhausted: {}",
                                record.topic(),
                                record.partition(),
                                record.offset(),
                                exception.getMessage(),
                                exception),
                new FixedBackOff(500L, 3));
    }
}
