package org.ewsfi.ingestion.adapter.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.ewsfi.platform.outbox.OutboxEvent;
import org.ewsfi.platform.outbox.OutboxEventRepository;
import org.ewsfi.platform.outbox.OutboxEventStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves {@link PaymentInstructionReturnedAdapter#record} persists an outbox row in the same
 * transaction, per ADR-003. Does not exercise Kafka publication -- that path is already covered by
 * {@code OutboxPublisherWorkerTest} in {@code ews-platform-outbox-starter}.
 */
@SpringBootTest
class PaymentInstructionReturnedAdapterTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private PaymentInstructionReturnedAdapter adapter;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

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
    void recordsAnOutboxEventForTheAccount() {
        PaymentInstructionReturnedRequest request =
                new PaymentInstructionReturnedRequest(
                        "pi-test-001",
                        "acct-test-001",
                        "1500.00",
                        "GBP",
                        "R01",
                        "FINANCIAL",
                        "2026-09-24");

        adapter.record(request);

        var events = outboxEventRepository.findAll();
        assertThat(events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getEventType()).isEqualTo("payment.instruction.returned");
                            assertThat(event.getKafkaTopic()).isEqualTo("ews.canonical.account-transaction");
                            assertThat(event.getPartitionKey()).isEqualTo("acct-test-001");
                            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.NEW);
                            assertThat(event.getPayload()).contains("acct-test-001", "pi-test-001", "FINANCIAL");
                        });
    }
}
