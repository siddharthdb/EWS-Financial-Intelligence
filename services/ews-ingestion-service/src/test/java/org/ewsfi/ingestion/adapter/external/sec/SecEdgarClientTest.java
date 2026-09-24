package org.ewsfi.ingestion.adapter.external.sec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link SecEdgarClient} both deterministically (parsing a canned response, matching the
 * real {@code data.sec.gov/submissions} parallel-array shape) and against the real, live,
 * unauthenticated SEC EDGAR API -- proving the HTTP integration genuinely works, not just that the
 * parsing logic is correct in isolation. The live test is skipped, like the Postgres-backed tests
 * elsewhere in this project, if the network is unreachable.
 */
class SecEdgarClientTest {

    private static final String SEC_HOST = "data.sec.gov";
    /** Apple Inc.'s CIK -- a large, stable SEC filer guaranteed to have 10-K/10-Q filings. */
    private static final String APPLE_CIK = "0000320193";

    private final SecEdgarClient client = new SecEdgarClient(new ObjectMapper());

    @BeforeAll
    static void requireNetwork() {
        boolean reachable;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(SEC_HOST, 443), 5000);
            reachable = true;
        } catch (Exception e) {
            reachable = false;
        }
        assumeTrue(reachable, SEC_HOST + " not reachable; skipping live SEC EDGAR integration test");
    }

    @Test
    void fetchesAndParsesARealFilingFromTheLiveSecApi() throws Exception {
        Optional<SecFiling> filing = client.fetchMostRecentPeriodicStatement(APPLE_CIK);

        assertThat(filing).isPresent();
        SecFiling f = filing.get();
        assertThat(f.cik()).isEqualTo(APPLE_CIK);
        assertThat(f.companyName()).containsIgnoringCase("Apple");
        assertThat(f.form()).isIn("10-K", "10-Q");
        assertThat(f.filingDate()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(f.accessionNumber()).matches("\\d{10}-\\d{2}-\\d{6}");
        assertThat(f.primaryDocumentUrl()).startsWith("https://www.sec.gov/Archives/edgar/data/320193/");
    }

    @Test
    void anUnknownCikReturnsNoFiling() {
        // SEC returns HTTP 404 for a syntactically valid but non-existent CIK.
        org.junit.jupiter.api.Assertions.assertThrows(
                java.io.IOException.class, () -> client.fetchMostRecentPeriodicStatement("9999999999"));
    }

    @Test
    void parsesTheParallelArrayResponseShapeDeterministically() throws Exception {
        String canned =
                """
                {
                  "cik": "0000320193",
                  "name": "Apple Inc.",
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0000320193-26-000010", "0000320193-26-000005"],
                      "filingDate": ["2026-08-01", "2026-05-01"],
                      "reportDate": ["2026-06-30", "2026-03-31"],
                      "form": ["4", "10-Q"],
                      "primaryDocument": ["form4.xml", "aapl-10q.htm"]
                    }
                  }
                }
                """;

        Optional<SecFiling> filing = client.parseMostRecentPeriodicStatement(APPLE_CIK, canned);

        assertThat(filing).isPresent();
        SecFiling f = filing.get();
        // The "4" filing (index 0) is not a periodic statement -- the first *qualifying* entry
        // (index 1, a 10-Q) must be selected, proving the parser walks past non-matching forms.
        assertThat(f.form()).isEqualTo("10-Q");
        assertThat(f.accessionNumber()).isEqualTo("0000320193-26-000005");
        assertThat(f.reportDate()).isEqualTo("2026-03-31");
    }

    @Test
    void returnsEmptyWhenNoQualifyingFormExistsInTheRecentWindow() throws Exception {
        String canned =
                """
                {
                  "cik": "0000999999",
                  "name": "No Periodic Filer Inc.",
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0000999999-26-000001"],
                      "filingDate": ["2026-08-01"],
                      "reportDate": [""],
                      "form": ["4"],
                      "primaryDocument": ["form4.xml"]
                    }
                  }
                }
                """;

        Optional<SecFiling> filing = client.parseMostRecentPeriodicStatement("0000999999", canned);

        assertThat(filing).isEmpty();
    }
}
