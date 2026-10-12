package org.ewsfi.ingestion.adapter.facility;

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
class FacilityCapacityAdapterTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private FacilityCapacityAdapter adapter;

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
    void recordsLimitAndOutstandingOnTheFacilityTopic() {
        adapter.recordLimit(new FacilityCapacityRequest("fac-util-1", "cpty-util-1", "1000.00", "GBP", "SANCTIONED_LIMIT"));
        adapter.recordOutstanding(new FacilityOutstandingRequest("fac-util-1", "cpty-util-1", "950.00", "GBP"));

        assertThat(outboxEventRepository.findAll())
                .anySatisfy(
                        event -> {
                            assertThat(event.getEventType()).isEqualTo("facility.limit.changed");
                            assertThat(event.getKafkaTopic()).isEqualTo("ews.canonical.facility");
                            assertThat(event.getPartitionKey()).isEqualTo("fac-util-1");
                            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.NEW);
                        })
                .anySatisfy(
                        event -> {
                            assertThat(event.getEventType()).isEqualTo("facility.outstanding.changed");
                            assertThat(event.getPartitionKey()).isEqualTo("fac-util-1");
                        });
    }
}
