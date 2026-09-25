package org.ewsfi.ingestion.adapter.external.sec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.ewsfi.platform.outbox.OutboxEventRepository;
import org.ewsfi.platform.outbox.OutboxEventStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves {@link SecFilingIngestionAdapter#recordReceived}/{@link SecFilingIngestionAdapter#recordValidated}
 * persist an outbox row in the same transaction, per ADR-003. Exercises the mapping/staging logic
 * deterministically against a constructed {@link SecFiling} rather than a live SEC fetch (that
 * end-to-end HTTP path is proven separately by {@link SecEdgarClientTest}), mirroring how the rest
 * of this project's adapter tests isolate Postgres-backed persistence behavior from external I/O.
 */
@SpringBootTest
class SecFilingIngestionAdapterTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private SecFilingIngestionAdapter adapter;

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
    void recordsAnOutboxEventForAFiling() {
        SecFiling filing =
                new SecFiling(
                        "0000320193",
                        "Apple Inc.",
                        "10-Q",
                        "2026-05-01",
                        "2026-03-31",
                        "0000320193-26-000005",
                        "aapl-10q.htm");

        adapter.recordReceived(filing);

        var events = outboxEventRepository.findAll();
        assertThat(events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getEventType()).isEqualTo("financial.statement.received");
                            assertThat(event.getKafkaTopic()).isEqualTo("ews.canonical.financial-statement");
                            assertThat(event.getPartitionKey()).isEqualTo("0000320193");
                            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.NEW);
                            assertThat(event.getPayload())
                                    .contains("0000320193", "Apple Inc.", "10-Q", "0000320193-26-000005");
                        });
    }

    @Test
    void recordsAValidatedOutboxEventCarryingXbrlFacts() {
        SecFiling filing =
                new SecFiling(
                        "0000320193",
                        "Apple Inc.",
                        "10-Q",
                        "2026-05-01",
                        "2026-03-31",
                        "0000320193-26-000005",
                        "aapl-10q.htm");
        Map<String, Long> facts = new LinkedHashMap<>();
        facts.put("Assets", 383266000000L);
        facts.put("Liabilities", 275746000000L);
        facts.put("StockholdersEquity", 107520000000L);

        adapter.recordValidated(filing, facts);

        var events = outboxEventRepository.findAll();
        assertThat(events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getEventType()).isEqualTo("financial.statement.validated");
                            assertThat(event.getKafkaTopic()).isEqualTo("ews.canonical.financial-statement");
                            assertThat(event.getPartitionKey()).isEqualTo("0000320193");
                            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.NEW);
                            assertThat(event.getPayload())
                                    .contains("0000320193", "0000320193-26-000005", "383266000000", "275746000000");
                        });
    }
}
