package org.ewsfi.persistence.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test against a real Postgres instance with
 * db/migration/V1__init_phase1_baseline.sql already applied (matches the docker-compose.yml
 * {@code postgres} service: database {@code ews}, user/password {@code ews}/{@code ews}). Proves
 * the hand-written entities in this module actually map onto the governed DDL, not just that they
 * compile.
 *
 * <p>Skips (does not fail) when no such Postgres instance is reachable, so {@code mvn verify}
 * stays green in environments without a running database -- CI provisions one explicitly (see
 * .github/workflows/ci.yml), and local development starts one via {@code docker compose up postgres}
 * or the equivalent documented in docs/architecture/07-build-log.md.
 */
@SpringBootTest(classes = SignalInstanceTestApplication.class)
class SignalInstanceRepositoryTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private SignalInstanceRepository signalInstanceRepository;

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
    @Transactional
    void persistsSignalInstanceWithEvidenceAndDispositionTag() {
        OffsetDateTime now = OffsetDateTime.now();
        SignalInstance signal =
                new SignalInstance(
                        UUID.randomUUID().toString(),
                        "REPEATED_PAYMENT_RETURN",
                        "GLOBAL_CORE",
                        "ACCOUNT",
                        "test-account-001",
                        "PROPOSED",
                        "MEDIUM",
                        0.8,
                        "MEDIUM",
                        now,
                        now,
                        now,
                        "POL-PAYMENT-RETURN-CORP-001",
                        "1.0",
                        "COMPLETE",
                        Set.of("feature-value-" + UUID.randomUUID()));

        SignalInstance saved = signalInstanceRepository.save(signal);

        List<SignalInstance> proposed = signalInstanceRepository.findByStatus("PROPOSED");
        assertThat(proposed).anyMatch(s -> s.getSignalId().equals(saved.getSignalId()));
        assertThat(saved.getEvidenceIds()).isNotEmpty();
    }

    @Test
    void constructorRejectsEmptyEvidence() {
        OffsetDateTime now = OffsetDateTime.now();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SignalInstance(
                                UUID.randomUUID().toString(),
                                "REPEATED_PAYMENT_RETURN",
                                "GLOBAL_CORE",
                                "ACCOUNT",
                                "test-account-002",
                                "PROPOSED",
                                "MEDIUM",
                                0.8,
                                "MEDIUM",
                                now,
                                now,
                                now,
                                "POL-PAYMENT-RETURN-CORP-001",
                                "1.0",
                                "COMPLETE",
                                Set.of()));
    }
}
