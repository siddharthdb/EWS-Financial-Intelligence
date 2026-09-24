package org.ewsfi.platform.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

/**
 * End-to-end test of the claim -> publish -> mark-published path (ADR-003, Section 11 of
 * docs/architecture/03-event-architecture.md) against a real embedded Kafka broker (in-process, no
 * Docker required) and a real Postgres instance with db/migration/V1__init_phase1_baseline.sql
 * applied.
 *
 * <p>Skips (does not fail) when no such Postgres instance is reachable, matching the pattern in
 * {@code ews-persistence-core}'s {@code SignalInstanceRepositoryTest}.
 */
@SpringBootTest(classes = OutboxTestApplication.class)
@EmbeddedKafka(partitions = 1, topics = {"test.ews.canonical.account-transaction"})
@DirtiesContext
class OutboxPublisherWorkerTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";
    private static final String TOPIC = "test.ews.canonical.account-transaction";

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxPublisherWorker publisherWorker;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @BeforeAll
    static void requirePostgres() {
        boolean reachable;
        try (Connection ignored = DriverManager.getConnection(JDBC_URL, "ews", "ews")) {
            reachable = true;
        } catch (SQLException e) {
            reachable = false;
        }
        assumeTrue(reachable, "Postgres not reachable at " + JDBC_URL + "; skipping integration test");
    }

    @Test
    void claimsAndPublishesAnOutboxEventThenMarksItPublished() throws Exception {
        String payloadJson = "{\"paymentInstructionId\":\"pi-001\",\"accountId\":\"acct-001\"}";
        OutboxEvent event =
                OutboxEvent.newEvent(
                        "PaymentInstruction",
                        "pi-001",
                        "payment.instruction.returned",
                        "v1",
                        "acct-001",
                        TOPIC,
                        payloadJson,
                        null);
        OutboxEvent saved = repository.save(event);
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.NEW);

        publisherWorker.publishClaimedBatch();

        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps("outbox-test-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> consumer =
                new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps)) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, TOPIC);
            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(10));
            assertThat(record.key()).isEqualTo("acct-001");
            // Compared as parsed JSON, not raw strings: Postgres's jsonb column type canonicalizes
            // the stored payload (reorders object keys, normalizes whitespace) between the insert
            // and the claim-query read-back, so the published bytes are semantically but not
            // byte-for-byte identical to what was originally saved -- expected jsonb behavior, not
            // a bug in the outbox pipeline itself.
            ObjectMapper mapper = new ObjectMapper();
            JsonNode expected = mapper.readTree(payloadJson);
            JsonNode actual = mapper.readTree(record.value());
            assertThat(actual).isEqualTo(expected);
        }

        OutboxEvent reloaded = repository.findById(saved.getEventId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }
}
