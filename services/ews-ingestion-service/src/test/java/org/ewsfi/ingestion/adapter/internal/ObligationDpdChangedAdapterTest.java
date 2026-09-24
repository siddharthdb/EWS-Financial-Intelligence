package org.ewsfi.ingestion.adapter.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.ewsfi.platform.outbox.OutboxEventRepository;
import org.ewsfi.platform.outbox.OutboxEventStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves {@link ObligationDpdChangedAdapter#record} persists an outbox row in the same
 * transaction, per ADR-003. Mirrors {@code PaymentInstructionReturnedAdapterTest}.
 */
@SpringBootTest
class ObligationDpdChangedAdapterTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private ObligationDpdChangedAdapter adapter;

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
    void recordsAnOutboxEventForTheFacility() {
        ObligationDpdChangedRequest request =
                new ObligationDpdChangedRequest("fac-test-001", "cp-test-001", 0, 5, "2026-09-24");

        adapter.record(request);

        var events = outboxEventRepository.findAll();
        assertThat(events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getEventType()).isEqualTo("obligation.dpd.changed");
                            assertThat(event.getKafkaTopic()).isEqualTo("ews.canonical.repayment");
                            assertThat(event.getPartitionKey()).isEqualTo("fac-test-001");
                            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.NEW);
                            assertThat(event.getPayload()).contains("fac-test-001", "cp-test-001");
                        });
    }
}
