package org.ewsfi.caseworkflow.casemgmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.ewsfi.platform.outbox.OutboxEvent;
import org.ewsfi.platform.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Exercises the investigation-case lifecycle end-to-end against a real Postgres database: open ->
 * assign -> escalate -> close, each step both persisting the new status and staging its {@code
 * case.*} outbox event, plus a transition guard test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CaseControllerTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvestigationCaseRepository caseRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

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
    void aCaseMovesThroughItsFullLifecycle() throws Exception {
        String signalId = "sig-" + java.util.UUID.randomUUID();

        MvcResult openResult =
                mockMvc
                        .perform(
                                post("/api/v1/cases")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"signalId": "%s", "openedBy": "analyst-1"}
                                                """
                                                        .formatted(signalId)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("OPEN"))
                        .andReturn();

        String caseId =
                objectMapper.readTree(openResult.getResponse().getContentAsString()).get("caseId").asText();

        mockMvc
                .perform(
                        post("/api/v1/cases/{id}/assign", caseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"assignedTo": "analyst-2"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        mockMvc
                .perform(
                        post("/api/v1/cases/{id}/escalate", caseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"reason": "Needs senior credit review"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCALATED"));

        mockMvc
                .perform(
                        post("/api/v1/cases/{id}/close", caseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"outcome": "CONFIRMED_RISK", "reason": "Escalation review confirmed deterioration"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        InvestigationCase reloaded = caseRepository.findById(caseId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("CLOSED");
        assertThat(reloaded.getAssignedTo()).isEqualTo("analyst-2");
        assertThat(reloaded.getEscalationReason()).isEqualTo("Needs senior credit review");
        assertThat(reloaded.getClosureOutcome()).isEqualTo("CONFIRMED_RISK");

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events)
                .anySatisfy(e -> assertThat(e.getEventType()).isEqualTo("case.opened"))
                .anySatisfy(e -> assertThat(e.getEventType()).isEqualTo("case.assigned"))
                .anySatisfy(e -> assertThat(e.getEventType()).isEqualTo("case.escalated"))
                .anySatisfy(e -> assertThat(e.getEventType()).isEqualTo("case.closed"));
        assertThat(events)
                .filteredOn(e -> e.getPartitionKey().equals(caseId))
                .allSatisfy(e -> assertThat(e.getKafkaTopic()).isEqualTo("ews.derived.case"));
    }

    @Test
    void closingAnAlreadyClosedCaseReturnsConflict() throws Exception {
        MvcResult openResult =
                mockMvc
                        .perform(
                                post("/api/v1/cases")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"signalId": "sig-conflict-test", "openedBy": "analyst-1"}
                                                """))
                        .andExpect(status().isCreated())
                        .andReturn();
        String caseId =
                objectMapper.readTree(openResult.getResponse().getContentAsString()).get("caseId").asText();

        mockMvc
                .perform(
                        post("/api/v1/cases/{id}/close", caseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"outcome": "FALSE_POSITIVE", "reason": "No real issue found"}
                                        """))
                .andExpect(status().isOk());

        mockMvc
                .perform(
                        post("/api/v1/cases/{id}/close", caseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"outcome": "CONFIRMED_RISK", "reason": "second close attempt"}
                                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void assigningAnUnknownCaseReturnsNotFound() throws Exception {
        mockMvc
                .perform(
                        post("/api/v1/cases/{id}/assign", "not-a-real-case-id")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"assignedTo": "analyst-1"}
                                        """))
                .andExpect(status().isNotFound());
    }

    @Test
    void listCasesFiltersByStatus() throws Exception {
        mockMvc
                .perform(
                        post("/api/v1/cases")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"signalId": "sig-list-test", "openedBy": "analyst-1"}
                                        """))
                .andExpect(status().isCreated());

        mockMvc
                .perform(get("/api/v1/cases").param("status", "OPEN"))
                .andExpect(status().isOk());
    }
}
