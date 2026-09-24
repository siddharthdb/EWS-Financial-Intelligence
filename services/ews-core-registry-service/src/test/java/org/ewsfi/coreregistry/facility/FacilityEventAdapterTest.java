package org.ewsfi.coreregistry.facility;

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
 * Proves {@link FacilityEventAdapter} persists an outbox row in the same transaction for both
 * event types it records, per ADR-003. Mirrors {@code ObligationDpdChangedAdapterTest}.
 */
@SpringBootTest
class FacilityEventAdapterTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private FacilityEventAdapter adapter;

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
    void recordsAnOutboxEventForALimitChange() {
        FacilityLimitChangedRequest request =
                new FacilityLimitChangedRequest("cp-test-001", 100000.0, "USD", "2026-09-24");

        adapter.recordLimitChanged("fac-test-001", request);

        var events = outboxEventRepository.findAll();
        assertThat(events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getEventType()).isEqualTo("facility.limit.changed");
                            assertThat(event.getKafkaTopic()).isEqualTo("ews.canonical.facility");
                            assertThat(event.getPartitionKey()).isEqualTo("fac-test-001");
                            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.NEW);
                            assertThat(event.getPayload()).contains("fac-test-001", "cp-test-001", "100000");
                        });
    }

    @Test
    void recordsAnOutboxEventForAnOutstandingChange() {
        FacilityOutstandingChangedRequest request =
                new FacilityOutstandingChangedRequest("cp-test-002", 85000.0, "USD", "2026-09-24");

        adapter.recordOutstandingChanged("fac-test-002", request);

        var events = outboxEventRepository.findAll();
        assertThat(events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getEventType()).isEqualTo("facility.outstanding.changed");
                            assertThat(event.getKafkaTopic()).isEqualTo("ews.canonical.facility");
                            assertThat(event.getPartitionKey()).isEqualTo("fac-test-002");
                            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.NEW);
                            assertThat(event.getPayload()).contains("fac-test-002", "cp-test-002", "85000");
                        });
    }
}
