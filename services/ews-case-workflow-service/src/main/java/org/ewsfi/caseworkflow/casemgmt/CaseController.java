package org.ewsfi.caseworkflow.casemgmt;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.platform.outbox.OutboxEvent;
import org.ewsfi.platform.outbox.OutboxEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The minimal case/decision model beyond a single signal disposition (roadmap item 1.15): an
 * investigation case's {@code open -> assign -> escalate -> close} lifecycle, per
 * docs/architecture/03-event-architecture.md Section 3 ({@code case.opened}, {@code
 * case.assigned}, {@code case.escalated}, {@code case.closed} on {@code ews.derived.case}). Every
 * transition both updates {@code investigation_case.status} and stages the corresponding event via
 * the outbox (ADR-003), in the same local transaction as the state change.
 *
 * <p>Unlike {@link org.ewsfi.caseworkflow.disposition.SignalDispositionController}, which is the
 * mandatory single-step human-validation gate every signal passes through, opening an
 * investigation case is optional follow-up work an analyst does after accepting a signal -- a
 * case is not required for a disposition to be recorded. {@code openedBy}/{@code assignedTo} are
 * caller-supplied identifiers, not authenticated principals, pending roadmap item 1.16 (not
 * started) -- the same documented gap {@code SignalDispositionController} already carries.
 */
@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private static final String CASE_TOPIC = "ews.derived.case";
    private static final Set<String> ASSIGNABLE_FROM = Set.of("OPEN", "ASSIGNED");
    private static final Set<String> ESCALATABLE_FROM = Set.of("OPEN", "ASSIGNED");
    private static final Set<String> CLOSABLE_FROM = Set.of("OPEN", "ASSIGNED", "ESCALATED");

    private final InvestigationCaseRepository caseRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public CaseController(
            InvestigationCaseRepository caseRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.caseRepository = caseRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<InvestigationCase> listCases(
            @RequestParam(name = "status", required = false) String status) {
        return status == null ? caseRepository.findAll() : caseRepository.findByStatus(status);
    }

    @GetMapping("/{caseId}")
    public InvestigationCase getCase(@PathVariable("caseId") String caseId) {
        return findOrThrow(caseId);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<InvestigationCase> openCase(@RequestBody CaseOpenRequest request) {
        String caseId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();

        InvestigationCase investigationCase =
                new InvestigationCase(caseId, request.signalId(), request.openedBy(), now);
        InvestigationCase saved = caseRepository.save(investigationCase);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("caseId", caseId);
        data.put("signalId", request.signalId());
        data.put("openedBy", request.openedBy());
        publishCaseEvent("case.opened", caseId, data);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/{caseId}/assign")
    @Transactional
    public ResponseEntity<InvestigationCase> assignCase(
            @PathVariable("caseId") String caseId, @RequestBody CaseAssignRequest request) {
        InvestigationCase investigationCase = findOrThrow(caseId);
        requireStatus(investigationCase, ASSIGNABLE_FROM, "assigned");

        investigationCase.setAssignedTo(request.assignedTo());
        investigationCase.setAssignedAt(OffsetDateTime.now());
        investigationCase.setStatus("ASSIGNED");
        InvestigationCase saved = caseRepository.save(investigationCase);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("caseId", caseId);
        data.put("assignedTo", request.assignedTo());
        publishCaseEvent("case.assigned", caseId, data);

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{caseId}/escalate")
    @Transactional
    public ResponseEntity<InvestigationCase> escalateCase(
            @PathVariable("caseId") String caseId, @RequestBody CaseEscalateRequest request) {
        InvestigationCase investigationCase = findOrThrow(caseId);
        requireStatus(investigationCase, ESCALATABLE_FROM, "escalated");

        investigationCase.setEscalationReason(request.reason());
        investigationCase.setEscalatedAt(OffsetDateTime.now());
        investigationCase.setStatus("ESCALATED");
        InvestigationCase saved = caseRepository.save(investigationCase);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("caseId", caseId);
        data.put("reason", request.reason());
        publishCaseEvent("case.escalated", caseId, data);

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{caseId}/close")
    @Transactional
    public ResponseEntity<InvestigationCase> closeCase(
            @PathVariable("caseId") String caseId, @RequestBody CaseCloseRequest request) {
        InvestigationCase investigationCase = findOrThrow(caseId);
        requireStatus(investigationCase, CLOSABLE_FROM, "closed");

        investigationCase.setClosureOutcome(request.outcome());
        investigationCase.setClosureReason(request.reason());
        investigationCase.setClosedAt(OffsetDateTime.now());
        investigationCase.setStatus("CLOSED");
        InvestigationCase saved = caseRepository.save(investigationCase);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("caseId", caseId);
        data.put("outcome", request.outcome());
        data.put("reason", request.reason());
        publishCaseEvent("case.closed", caseId, data);

        return ResponseEntity.ok(saved);
    }

    private InvestigationCase findOrThrow(String caseId) {
        return caseRepository
                .findById(caseId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No case with id " + caseId));
    }

    private void requireStatus(InvestigationCase investigationCase, Set<String> allowed, String action) {
        if (!allowed.contains(investigationCase.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Case "
                            + investigationCase.getCaseId()
                            + " cannot be "
                            + action
                            + " from status "
                            + investigationCase.getStatus());
        }
    }

    private void publishCaseEvent(String eventType, String caseId, Map<String, Object> data) {
        String eventId = UUID.randomUUID().toString();
        JsonEventEnvelope envelope =
                new JsonEventEnvelope(eventId, eventType, Instant.now().toString(), "CASE", caseId, data);
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonEventEnvelope", e);
        }

        OutboxEvent outboxEvent =
                OutboxEvent.newEvent("Case", caseId, eventType, "v1", caseId, CASE_TOPIC, payloadJson, null);
        outboxEventRepository.save(outboxEvent);
    }
}
