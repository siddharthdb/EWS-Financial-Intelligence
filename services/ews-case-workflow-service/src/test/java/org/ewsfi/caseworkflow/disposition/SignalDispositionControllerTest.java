package org.ewsfi.caseworkflow.disposition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.ewsfi.persistence.signal.SignalInstance;
import org.ewsfi.persistence.signal.SignalInstanceRepository;
import org.ewsfi.platform.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the mandatory human-validation gate end-to-end against a real Postgres database: a
 * {@code PROPOSED} signal moves to {@code ACCEPTED}, and an outbox row for
 * {@code signal.disposition.recorded} is created in the same request.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SignalDispositionControllerTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignalInstanceRepository signalInstanceRepository;

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
    void acceptingAProposedSignalUpdatesStatusAndPublishesDispositionEvent() throws Exception {
        SignalInstance signal = aProposedSignal();
        signalInstanceRepository.save(signal);

        mockMvc
                .perform(
                        get("/api/v1/signals").param("status", "PROPOSED"))
                .andExpect(status().isOk());

        mockMvc
                .perform(
                        post("/api/v1/signals/{id}/disposition", signal.getSignalId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"disposition": "ACCEPTED", "reason": "Confirmed liquidity stress on review"}
                                        """))
                .andExpect(status().isOk());

        SignalInstance reloaded = signalInstanceRepository.findById(signal.getSignalId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("ACCEPTED");

        List<org.ewsfi.platform.outbox.OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents)
                .anySatisfy(
                        event -> {
                            assertThat(event.getEventType()).isEqualTo("signal.disposition.recorded");
                            assertThat(event.getPartitionKey()).isEqualTo(signal.getSignalId());
                            assertThat(event.getPayload()).contains("ACCEPT", signal.getSignalId());
                        });
    }

    @Test
    void rejectingASignalTwiceReturnsConflict() throws Exception {
        SignalInstance signal = aProposedSignal();
        signalInstanceRepository.save(signal);

        mockMvc
                .perform(
                        post("/api/v1/signals/{id}/disposition", signal.getSignalId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"disposition": "REJECTED", "reason": "False positive"}
                                        """))
                .andExpect(status().isOk());

        mockMvc
                .perform(
                        post("/api/v1/signals/{id}/disposition", signal.getSignalId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"disposition": "ACCEPTED", "reason": "changed my mind"}
                                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidDispositionValueReturnsBadRequest() throws Exception {
        SignalInstance signal = aProposedSignal();
        signalInstanceRepository.save(signal);

        mockMvc
                .perform(
                        post("/api/v1/signals/{id}/disposition", signal.getSignalId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"disposition": "MAYBE", "reason": "unsure"}
                                        """))
                .andExpect(status().isBadRequest());
    }

    private static SignalInstance aProposedSignal() {
        OffsetDateTime now = OffsetDateTime.now();
        return new SignalInstance(
                UUID.randomUUID().toString(),
                "REPEATED_PAYMENT_RETURN",
                "GLOBAL_CORE",
                "ACCOUNT",
                "acct-disposition-test",
                "PROPOSED",
                "MEDIUM",
                0.8,
                "MEDIUM",
                now,
                now,
                now,
                "POL-PAYMENT-RETURN-CORP-001",
                "2.0",
                "COMPLETE",
                Set.of("fv-" + UUID.randomUUID()));
    }
}
