package org.ewsfi.experience.signals;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.ewsfi.persistence.feature.FeatureValue;
import org.ewsfi.persistence.feature.FeatureValueRepository;
import org.ewsfi.persistence.signal.SignalInstance;
import org.ewsfi.persistence.signal.SignalInstanceRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the evidence drill-down actually resolves: a signal whose {@code evidenceIds} reference a
 * real {@code feature_value} row returns that feature value's details in the response, against the
 * real local Postgres database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProposedSignalQueryControllerTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignalInstanceRepository signalInstanceRepository;

    @Autowired
    private FeatureValueRepository featureValueRepository;

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
    void returnsAProposedSignalWithItsResolvedEvidence() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        String featureValueId = "fv-" + UUID.randomUUID();
        FeatureValue featureValue =
                new FeatureValue(
                        featureValueId,
                        "FD-RETURNED-PAYMENT-COUNT-30D-001",
                        "returned_payment_count_30d",
                        "1.0",
                        "ACCOUNT",
                        "acct-experience-test",
                        "VALUE",
                        "INTEGER",
                        new BigDecimal("3"),
                        now,
                        now,
                        now.minusDays(30),
                        now,
                        "COMPLETE",
                        "1.0");
        featureValueRepository.save(featureValue);

        String signalId = UUID.randomUUID().toString();
        SignalInstance signal =
                new SignalInstance(
                        signalId,
                        "REPEATED_PAYMENT_RETURN",
                        "GLOBAL_CORE",
                        "ACCOUNT",
                        "acct-experience-test",
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
                        Set.of(featureValueId));
        signalInstanceRepository.save(signal);

        mockMvc
                .perform(get("/api/v1/proposed-signals/{id}", signalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signalId").value(signalId))
                .andExpect(jsonPath("$.signalType").value("REPEATED_PAYMENT_RETURN"))
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.evidence", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.evidence[0].featureValueId").value(featureValueId))
                .andExpect(jsonPath("$.evidence[0].featureName").value("returned_payment_count_30d"))
                .andExpect(jsonPath("$.evidence[0].valueNumeric").value(3));

        mockMvc
                .perform(get("/api/v1/proposed-signals").param("status", "PROPOSED"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[?(@.signalId == '" + signalId + "')]").exists());
    }

    @Test
    void unknownSignalIdReturns404() throws Exception {
        mockMvc
                .perform(get("/api/v1/proposed-signals/{id}", "does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
