package org.ewsfi.ingestion.adapter.external.sec;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Triggers a real, on-demand sync of a single company's most recent SEC EDGAR periodic filing.
 * Unlike every other Phase-1 ingestion controller (which stands in for an internal system that
 * would call the adapter in production), this one genuinely calls the live external SEC API when
 * invoked -- there is no internal system to stand in for; SEC EDGAR itself is the source, per
 * roadmap item 2.1.
 */
@RestController
@RequestMapping("/api/v1/external/sec-filings")
public class SecFilingSyncController {

    private final SecFilingIngestionAdapter adapter;

    public SecFilingSyncController(SecFilingIngestionAdapter adapter) {
        this.adapter = adapter;
    }

    @PostMapping("/sync/{cik}")
    public ResponseEntity<Map<String, Object>> syncMostRecentPeriodicStatement(
            @PathVariable("cik") String cik) {
        List<String> eventIds;
        try {
            eventIds = adapter.syncMostRecentPeriodicStatement(cik);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Failed to reach SEC EDGAR for CIK " + cik + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted");
        }

        if (eventIds.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("message", "No 10-K/10-Q filing found in SEC's recent filings for CIK " + cik));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("eventIds", eventIds));
    }
}
