package org.ewsfi.featureprocessor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Retry-with-backoff error handling for this service's {@code @KafkaListener}s (roadmap item 3.6,
 * production hardening). Spring Boot's autoconfigured listener container factory automatically
 * picks up any {@link CommonErrorHandler} bean present in the context
 * ({@code ConcurrentKafkaListenerContainerFactoryConfigurer}), so no manual factory redefinition
 * is needed here.
 *
 * <p>Fixes a real gap found while building this: {@link org.ewsfi.featureprocessor.persistence.FeatureValuePersistenceListener}
 * previously caught every exception internally and only logged a warning, so a transient failure
 * (e.g. a momentary Postgres connection blip) silently dropped the feature value forever -- no
 * retry, no visibility beyond a log line, and Kafka's offset still committed as if processing had
 * succeeded. That listener no longer catches broadly; exceptions now propagate to this handler,
 * which retries a fixed number of times before logging a clear, unambiguous permanent-failure
 * record (a "poor man's" dead-letter -- a real dead-letter topic is a documented follow-up, not
 * implemented here).
 *
 * <p>Retries apply uniformly to every exception (a permanently malformed payload is retried the
 * same as a transient DB error) -- a documented simplification; distinguishing retryable from
 * non-retryable exception types is a natural follow-up once a concrete non-retryable case is
 * observed in practice.
 */
@Configuration
public class KafkaListenerErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerErrorHandlingConfig.class);

    @Bean
    public CommonErrorHandler kafkaListenerErrorHandler() {
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
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
        return errorHandler;
    }
}
