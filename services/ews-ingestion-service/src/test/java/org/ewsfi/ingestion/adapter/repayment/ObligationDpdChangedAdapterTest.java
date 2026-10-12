package org.ewsfi.ingestion.adapter.repayment;

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
    void recordsAnOutboxEventKeyedByFacility() {
        ObligationDpdChangedRequest request =
                new ObligationDpdChangedRequest(
                        "fac-dpd-001",
                        "cpty-dpd-001",
                        "obl-dpd-001",
                        0,
                        12,
                        "2026-08-20",
                        "2026-09-01",
                        "POL-DPD-CALC-001",
                        "sched-9",
                        true);

        adapter.record(request);

        assertThat(outboxEventRepository.findAll())
                .anySatisfy(
                        event -> {
                            assertThat(event.getEventType()).isEqualTo("obligation.dpd.changed");
                            assertThat(event.getKafkaTopic()).isEqualTo("ews.canonical.repayment");
                            assertThat(event.getPartitionKey()).isEqualTo("fac-dpd-001");
                            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.NEW);
                            assertThat(event.getPayload())
                                    .contains("fac-dpd-001", "currentDpd", "obl-dpd-001", "cpty-dpd-001");
                        });
    }
}
