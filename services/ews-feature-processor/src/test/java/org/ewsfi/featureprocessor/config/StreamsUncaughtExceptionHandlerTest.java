package org.ewsfi.featureprocessor.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.junit.jupiter.api.Test;

/**
 * Proves {@link KafkaStreamsConfig#streamsUncaughtExceptionHandlerConfigurer()} is wired to
 * {@code REPLACE_THREAD} -- Kafka Streams' documented mitigation so an uncaught exception in one
 * topology's processing logic doesn't kill the entire single-threaded Streams client and halt
 * every topology in this application.
 *
 * <p>This deliberately does not attempt a live crash-and-recover integration test. An earlier
 * version of this test published a record with a non-numeric {@code currentDpd} to force {@link
 * org.ewsfi.featureprocessor.topology.MaxDpdFeatureTopology}'s aggregator to throw, then asserted
 * a subsequent valid record was still processed. That assertion is fundamentally unsound: Kafka
 * Streams' at-least-once semantics mean the crashing record's offset is never committed, so even
 * with {@code REPLACE_THREAD} the replacement thread re-reads and re-crashes on the very same
 * record indefinitely -- the partition never advances, so no later record on it is ever processed
 * either. {@code REPLACE_THREAD} keeps the client alive (useful defense-in-depth for genuinely
 * unanticipated failures) but cannot substitute for not crashing in the first place. The actual
 * fix for the non-numeric {@code currentDpd} case is defensive input filtering in {@link
 * org.ewsfi.featureprocessor.topology.MaxDpdFeatureTopology}, proven by {@code
 * MaxDpdFeatureTopologyTest#aMalformedNonNumericCurrentDpdIsSkippedRatherThanCrashingTheAggregator()}
 * using {@code TopologyTestDriver}, which can assert the record is skipped without needing to
 * reason about partition-level redelivery timing.
 */
class StreamsUncaughtExceptionHandlerTest {

    @Test
    void configurerSetsReplaceThreadAsTheUncaughtExceptionResponse() {
        KafkaStreamsConfig config = new KafkaStreamsConfig();
        StreamsUncaughtExceptionHandler handler =
                throwable -> {
                    throw new UnsupportedOperationException("not invoked by this test");
                };

        // The configurer is a lambda that calls factoryBean.setStreamsUncaughtExceptionHandler(...);
        // capture what it sets by handing it a minimal fake factory bean setter.
        java.util.concurrent.atomic.AtomicReference<StreamsUncaughtExceptionHandler> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        org.springframework.kafka.config.StreamsBuilderFactoryBean fakeFactoryBean =
                new org.springframework.kafka.config.StreamsBuilderFactoryBean() {
                    @Override
                    public void setStreamsUncaughtExceptionHandler(StreamsUncaughtExceptionHandler h) {
                        captured.set(h);
                    }
                };

        config.streamsUncaughtExceptionHandlerConfigurer().configure(fakeFactoryBean);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().handle(new RuntimeException("boom")))
                .isEqualTo(StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD);
    }
}
