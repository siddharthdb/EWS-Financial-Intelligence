package org.ewsfi.caseworkflow.casemgmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.ewsfi.persistence.casemgmt.CaseFile;
import org.ewsfi.persistence.casemgmt.CaseFileRepository;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class CaseControllerTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignalInstanceRepository signalInstanceRepository;

    @Autowired
    private CaseFileRepository caseFileRepository;

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
    void acceptedSignalMovesThroughOpenAssignEscalateAndClose() throws Exception {
        SignalInstance signal = acceptedSignal();
        signalInstanceRepository.save(signal);

        mockMvc.perform(post("/api/v1/cases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signalId\":\"" + signal.getSignalId() + "\",\"reason\":\"review\"}"))
                .andExpect(status().isUnauthorized());

        MvcResult opened =
                mockMvc.perform(
                                post("/api/v1/cases")
                                        .with(httpBasic("analyst", "analyst"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"signalId\":\""
                                                        + signal.getSignalId()
                                                        + "\",\"reason\":\"review\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        String caseId = opened.getResponse().getContentAsString().replaceAll(".*\"caseId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(
                        post("/api/v1/cases/{id}/assign", caseId)
                                .with(httpBasic("analyst", "analyst"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"assigneeId\":\"analyst-2\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/v1/cases/{id}/escalate", caseId)
                                .with(httpBasic("analyst", "analyst"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"needs supervisor\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/v1/cases/{id}/escalate", caseId)
                                .with(httpBasic("supervisor", "supervisor"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"needs supervisor\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/v1/cases/{id}/close", caseId)
                                .with(httpBasic("supervisor", "supervisor"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"outcome\":\"MITIGATED\"}"))
                .andExpect(status().isOk());

        CaseFile saved = caseFileRepository.findById(caseId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("CLOSED");
        assertThat(saved.getOutcome()).isEqualTo("MITIGATED");
        assertThat(signalInstanceRepository.findById(signal.getSignalId()).orElseThrow().getCaseId())
                .isEqualTo(caseId);
        assertThat(outboxEventRepository.findAll())
                .anySatisfy(event -> assertThat(event.getEventType()).isEqualTo("case.opened"));
    }

    private static SignalInstance acceptedSignal() {
        OffsetDateTime now = OffsetDateTime.now();
        SignalInstance signal =
                new SignalInstance(
                        UUID.randomUUID().toString(),
                        "REPEATED_PAYMENT_RETURN",
                        "GLOBAL_CORE",
                        "ACCOUNT",
                        "acct-case",
                        "ACCEPTED",
                        "MEDIUM",
                        0.8,
                        "MEDIUM",
                        now,
                        now,
                        now,
                        "POL-PAYMENT-RETURN-CORP-001",
                        "2.0",
                        "COMPLETE",
                        Set.of("fv-case"));
        return signal;
    }
}
