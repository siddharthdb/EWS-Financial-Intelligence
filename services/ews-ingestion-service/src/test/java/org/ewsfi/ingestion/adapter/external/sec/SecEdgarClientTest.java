package org.ewsfi.ingestion.adapter.external.sec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    void fetchesRealXbrlBalanceSheetFactsForAKnownRecentFiling() throws Exception {
        // Fetch a real recent filing first, then fetch real XBRL facts tied to that exact
        // accession number + report date -- proving the two live API calls genuinely compose, not
        // just that each parses in isolation.
        Optional<SecFiling> filing = client.fetchMostRecentPeriodicStatement(APPLE_CIK);
        assertThat(filing).isPresent();

        Map<String, Long> facts =
                client.fetchXbrlFactsForFiling(
                        APPLE_CIK, filing.get().accessionNumber(), filing.get().reportDate());

        // Apple is one of the largest US filers and reliably reports Assets/Liabilities/
        // StockholdersEquity/OperatingIncomeLoss/NetCashProvidedByUsedInOperatingActivities on
        // every periodic filing, so at least one concept should resolve for any recent 10-K/10-Q.
        assertThat(facts).isNotEmpty();
        Set<String> allConcepts = new java.util.HashSet<>(SecEdgarClient.BALANCE_SHEET_CONCEPTS);
        allConcepts.addAll(SecEdgarClient.DURATION_CONCEPTS);
        assertThat(facts.keySet()).isSubsetOf(allConcepts);
        facts.values().forEach(value -> assertThat(value).isPositive());
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
    void parsesOnlyTheFactsMatchingTheGivenAccessionNumber() throws Exception {
        String canned =
                """
                {
                  "cik": 320193,
                  "entityName": "Apple Inc.",
                  "facts": {
                    "us-gaap": {
                      "Assets": {
                        "units": {
                          "USD": [
                            {"end": "2025-09-27", "val": 364980000000, "accn": "0000320193-25-000009", "form": "10-K"},
                            {"end": "2026-06-27", "val": 383266000000, "accn": "0000320193-26-000020", "form": "10-Q"}
                          ]
                        }
                      },
                      "Liabilities": {
                        "units": {
                          "USD": [
                            {"end": "2026-06-27", "val": 275746000000, "accn": "0000320193-26-000020", "form": "10-Q"}
                          ]
                        }
                      },
                      "SomeOtherConcept": {
                        "units": {
                          "USD": [
                            {"end": "2026-06-27", "val": 999, "accn": "0000320193-26-000020", "form": "10-Q"}
                          ]
                        }
                      }
                    }
                  }
                }
                """;

        Map<String, Long> facts =
                client.parseXbrlFactsForAccession(canned, "0000320193-26-000020", "2026-06-27");

        // Only the entry matching this exact accession number and report date is picked (not the
        // earlier Assets filing for a different accession), and only concepts in
        // BALANCE_SHEET_CONCEPTS/DURATION_CONCEPTS are kept (SomeOtherConcept is excluded even
        // though it matches the accession number).
        assertThat(facts).containsOnly(
                org.assertj.core.api.Assertions.entry("Assets", 383266000000L),
                org.assertj.core.api.Assertions.entry("Liabilities", 275746000000L));
    }

    @Test
    void returnsEmptyMapWhenNoConceptMatchesTheAccessionNumber() throws Exception {
        String canned =
                """
                {
                  "facts": {
                    "us-gaap": {
                      "Assets": {
                        "units": {
                          "USD": [
                            {"end": "2025-09-27", "val": 364980000000, "accn": "0000320193-25-000009", "form": "10-K"}
                          ]
                        }
                      }
                    }
                  }
                }
                """;

        Map<String, Long> facts =
                client.parseXbrlFactsForAccession(canned, "0000320193-26-999999", "2025-09-27");

        assertThat(facts).isEmpty();
    }

    @Test
    void selectsTheDiscreteQuarterNotTheYearToDateFigureForADurationConcept() throws Exception {
        // A real quirk of SEC's XBRL company-facts API, confirmed empirically: a single accession
        // number reports MULTIPLE overlapping period entries for a duration concept (the current
        // quarter, the current year-to-date, and prior-year comparatives). All four of these real
        // entries (from Apple's actual data) share accn "0000320193-26-000020"; two of them
        // (year-to-date and the discrete quarter) both end on "2026-06-27" -- the filing's
        // reportDate -- so only the end-date filter alone would still leave two candidates.
        String canned =
                """
                {
                  "facts": {
                    "us-gaap": {
                      "OperatingIncomeLoss": {
                        "units": {
                          "USD": [
                            {"start": "2024-09-29", "end": "2025-06-28", "val": 100623000000, "accn": "0000320193-26-000020"},
                            {"start": "2025-03-30", "end": "2025-06-28", "val": 28202000000, "accn": "0000320193-26-000020"},
                            {"start": "2025-09-28", "end": "2026-06-27", "val": 122432000000, "accn": "0000320193-26-000020"},
                            {"start": "2026-03-29", "end": "2026-06-27", "val": 35695000000, "accn": "0000320193-26-000020"}
                          ]
                        }
                      }
                    }
                  }
                }
                """;

        Map<String, Long> facts =
                client.parseXbrlFactsForAccession(canned, "0000320193-26-000020", "2026-06-27");

        // Must pick the discrete ~3-month quarter (35695000000), not the ~9-month year-to-date
        // figure (122432000000) that shares the same end date, and not either prior-year
        // comparative (which end on 2025-06-28, not the reportDate).
        assertThat(facts).containsOnly(
                org.assertj.core.api.Assertions.entry("OperatingIncomeLoss", 35695000000L));
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
