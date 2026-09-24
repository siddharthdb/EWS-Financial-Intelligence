package org.ewsfi.caseworkflow.disposition;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.ewsfi.contracts.interim.JsonSignalDisposition;
import org.ewsfi.persistence.signal.SignalInstance;
import org.ewsfi.persistence.signal.SignalInstanceRepository;
import org.ewsfi.platform.outbox.OutboxEvent;
import org.ewsfi.platform.outbox.OutboxEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The mandatory human-validation gate ADR-002 requires: a machine-detected signal moves from
 * {@code PROPOSED} to {@code ACCEPTED}/{@code REJECTED} only through an explicit decision recorded
 * here, per docs/architecture/01-architecture-blueprint.md Section 15. Every disposition both
 * updates {@code signal_instance.status} and publishes an immutable
 * {@code signal.disposition.recorded} event via the outbox (ADR-003) to
 * {@code ews.derived.decision} (docs/architecture/03c-topic-and-partition-strategy.md Section 2) --
 * that event stream is this slice's audit trail for human decisions
 * (docs/architecture/01-architecture-blueprint.md Section 16's evidence drill-down chain).
 *
 * <p>{@code actorId}/{@code actorRole} are placeholders ({@code "unauthenticated-analyst"} /
 * {@code "ANALYST"}) pending real authentication (roadmap item 1.16, not started) -- a known,
 * documented gap, not a claim that dispositions are properly attributed yet.
 */
@RestController
@RequestMapping("/api/v1/signals")
public class SignalDispositionController {

    private static final String DECISION_TOPIC = "ews.derived.decision";
    private static final Set<String> ALLOWED_DISPOSITIONS = Set.of("ACCEPTED", "REJECTED");

    private final SignalInstanceRepository signalInstanceRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public SignalDispositionController(
            SignalInstanceRepository signalInstanceRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.signalInstanceRepository = signalInstanceRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<SignalInstance> listSignals(
            @RequestParam(name = "status", defaultValue = "PROPOSED") String status) {
        return signalInstanceRepository.findByStatus(status);
    }

    @PostMapping("/{signalId}/disposition")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<SignalInstance> recordDisposition(
            @PathVariable("signalId") String signalId, @RequestBody SignalDispositionRequest request) {
        if (!ALLOWED_DISPOSITIONS.contains(request.disposition())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "disposition must be one of " + ALLOWED_DISPOSITIONS + ", got: " + request.disposition());
        }

        SignalInstance signal =
                signalInstanceRepository
                        .findById(signalId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "No signal with id " + signalId));

        if (!"PROPOSED".equals(signal.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Signal "
                            + signalId
                            + " is not PROPOSED (current status: "
                            + signal.getStatus()
                            + "); only a PROPOSED signal can receive a disposition");
        }

        signal.setStatus(request.disposition());
        SignalInstance saved = signalInstanceRepository.save(signal);

        publishDispositionEvent(saved, request);

        return ResponseEntity.ok(saved);
    }

    private void publishDispositionEvent(SignalInstance signal, SignalDispositionRequest request) {
        String decisionId = UUID.randomUUID().toString();
        JsonSignalDisposition disposition =
                new JsonSignalDisposition(
                        decisionId,
                        signal.getSignalId(),
                        signal.getEntityId(),
                        "ACCEPTED".equals(request.disposition()) ? "ACCEPT" : "REJECT",
                        request.disposition(),
                        request.reason(),
                        "unauthenticated-analyst",
                        "ANALYST",
                        Instant.now().toString());
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(disposition);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDisposition", e);
        }

        OutboxEvent event =
                OutboxEvent.newEvent(
                        "SignalInstance",
                        signal.getSignalId(),
                        "signal.disposition.recorded",
                        "v1",
                        signal.getSignalId(),
                        DECISION_TOPIC,
                        payloadJson,
                        null);
        outboxEventRepository.save(event);
    }
}
