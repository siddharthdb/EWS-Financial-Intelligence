package org.ewsfi.caseworkflow.casemgmt;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ewsfi.persistence.casemgmt.CaseFile;
import org.ewsfi.persistence.casemgmt.CaseFileRepository;
import org.ewsfi.persistence.signal.SignalInstance;
import org.ewsfi.persistence.signal.SignalInstanceRepository;
import org.ewsfi.platform.outbox.OutboxEvent;
import org.ewsfi.platform.outbox.OutboxEventRepository;
import org.ewsfi.security.LocalActor;
import org.springframework.http.HttpStatus;
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
 * Opens, assigns, escalates, and closes a case for an accepted signal. Closing requires the
 * local-dev {@code SUPERVISOR} role. A case is workflow state, not official EWS approval.
 */
@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private static final String CASE_TOPIC = "ews.derived.case";

    private final CaseFileRepository caseFileRepository;
    private final SignalInstanceRepository signalInstanceRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public CaseController(
            CaseFileRepository caseFileRepository,
            SignalInstanceRepository signalInstanceRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.caseFileRepository = caseFileRepository;
        this.signalInstanceRepository = signalInstanceRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<CaseFile> list(@RequestParam(name = "status", required = false) String status) {
        if (status == null || status.isBlank()) {
            return caseFileRepository.findAll();
        }
        return caseFileRepository.findByStatus(status);
    }

    @PostMapping
    @Transactional
    public CaseFile open(@RequestBody OpenCaseRequest request) {
        SignalInstance signal =
                signalInstanceRepository
                        .findById(request.signalId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Signal not found"));
        if (!"ACCEPTED".equals(signal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A case can only be opened for an ACCEPTED signal");
        }
        if (caseFileRepository.existsBySignalId(signal.getSignalId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This signal already has a case");
        }
        CaseFile opened =
                new CaseFile(
                        UUID.randomUUID().toString(),
                        signal.getSignalId(),
                        signal.getEntityType(),
                        signal.getEntityId(),
                        LocalActor.id(),
                        OffsetDateTime.now(),
                        request.reason());
        CaseFile saved = caseFileRepository.save(opened);
        signal.setCaseId(saved.getCaseId());
        signalInstanceRepository.save(signal);
        publish(
                "case.opened",
                saved.getCaseId(),
                Map.of(
                        "caseId", saved.getCaseId(),
                        "signalId", saved.getSignalId(),
                        "entityType", saved.getEntityType(),
                        "entityId", saved.getEntityId(),
                        "actorId", LocalActor.id(),
                        "actorRole", LocalActor.role(),
                        "reason", request.reason() == null ? "" : request.reason()));
        return saved;
    }

    @PostMapping("/{caseId}/assign")
    @Transactional
    public CaseFile assign(@PathVariable("caseId") String caseId, @RequestBody AssignCaseRequest request) {
        CaseFile caseFile = loadOpen(caseId);
        if ("ESCALATED".equals(caseFile.getStatus()) || "CLOSED".equals(caseFile.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot assign a " + caseFile.getStatus() + " case");
        }
        caseFile.assign(request.assigneeId());
        publish(
                "case.assigned",
                caseFile.getCaseId(),
                Map.of(
                        "caseId", caseFile.getCaseId(),
                        "assigneeId", request.assigneeId(),
                        "actorId", LocalActor.id(),
                        "actorRole", LocalActor.role()));
        return caseFileRepository.save(caseFile);
    }

    @PostMapping("/{caseId}/escalate")
    @Transactional
    public CaseFile escalate(@PathVariable("caseId") String caseId, @RequestBody EscalateCaseRequest request) {
        requireSupervisor();
        CaseFile caseFile = loadOpen(caseId);
        if ("CLOSED".equals(caseFile.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot escalate a closed case");
        }
        caseFile.escalate(request.reason());
        publish(
                "case.escalated",
                caseFile.getCaseId(),
                Map.of(
                        "caseId", caseFile.getCaseId(),
                        "actorId", LocalActor.id(),
                        "actorRole", LocalActor.role(),
                        "reason", request.reason() == null ? "" : request.reason()));
        return caseFileRepository.save(caseFile);
    }

    @PostMapping("/{caseId}/close")
    @Transactional
    public CaseFile close(@PathVariable("caseId") String caseId, @RequestBody CloseCaseRequest request) {
        requireSupervisor();
        CaseFile caseFile = loadOpen(caseId);
        if ("CLOSED".equals(caseFile.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Case is already closed");
        }
        caseFile.close(request.outcome(), OffsetDateTime.now());
        publish(
                "case.closed",
                caseFile.getCaseId(),
                Map.of(
                        "caseId", caseFile.getCaseId(),
                        "actorId", LocalActor.id(),
                        "actorRole", LocalActor.role(),
                        "outcome", request.outcome()));
        return caseFileRepository.save(caseFile);
    }

    private CaseFile loadOpen(String caseId) {
        return caseFileRepository
                .findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
    }

    private static void requireSupervisor() {
        if (!LocalActor.isSupervisor()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SUPERVISOR role is required");
        }
    }

    private void publish(String eventType, String caseId, Map<String, String> body) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize " + eventType, e);
        }
        outboxEventRepository.save(
                OutboxEvent.newEvent("CaseFile", caseId, eventType, "v1", caseId, CASE_TOPIC, json, null));
    }

    public record OpenCaseRequest(String signalId, String reason) {}

    public record AssignCaseRequest(String assigneeId) {}

    public record EscalateCaseRequest(String reason) {}

    public record CloseCaseRequest(String outcome) {}
}
