package org.ewsfi.platform.outbox;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes claimed outbox rows to Kafka outside the row-lock-holding transaction, per
 * docs/architecture/03-event-architecture.md Section 11 ("Producer reliability and transactional
 * outbox"). Delivery is at-least-once with stable {@code eventId} identity (used as the Kafka
 * message key alongside the row's own {@code partitionKey} header where finer partitioning is
 * needed): Kafka acknowledgement followed by publisher-process failure can produce a duplicate with
 * the same {@code eventId}; consumers must be idempotent per ADR-003.
 */
@Component
public class OutboxPublisherWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherWorker.class);
    private static final int BATCH_SIZE = 50;
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxClaimStrategy claimStrategy;
    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisherWorker(
            OutboxClaimStrategy claimStrategy,
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.claimStrategy = claimStrategy;
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${ews.outbox.publish-interval-ms:1000}")
    public void publishClaimedBatch() {
        List<OutboxEvent> claimed = claimStrategy.claimBatch(BATCH_SIZE);
        for (OutboxEvent event : claimed) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            SendResult<String, String> result =
                    kafkaTemplate
                            .send(event.getKafkaTopic(), event.getPartitionKey(), event.getPayload())
                            .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            markPublished(
                    event.getEventId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception e) {
            log.warn(
                    "Failed to publish outbox event {} (type {}) to topic {}: {}",
                    event.getEventId(),
                    event.getEventType(),
                    event.getKafkaTopic(),
                    e.getMessage());
            markFailed(event.getEventId(), e.getClass().getSimpleName(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markPublished(java.util.UUID eventId, int partition, long offset) {
        repository
                .findById(eventId)
                .ifPresent(
                        event -> {
                            event.markPublished(partition, offset);
                            repository.save(event);
                        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markFailed(java.util.UUID eventId, String errorCode, String errorMessage) {
        repository
                .findById(eventId)
                .ifPresent(
                        event -> {
                            event.markFailed(errorCode, errorMessage);
                            repository.save(event);
                        });
    }
}
