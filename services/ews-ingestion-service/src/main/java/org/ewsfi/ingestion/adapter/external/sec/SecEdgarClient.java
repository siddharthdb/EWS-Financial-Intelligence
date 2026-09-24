package org.ewsfi.ingestion.adapter.external.sec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Real HTTP client for SEC EDGAR's unauthenticated {@code data.sec.gov/submissions} REST API
 * (docs/research/us-uk-corporate-credit-data-source-landscape.md Section 2.1: "SEC `data.sec.gov`
 * exposes unauthenticated REST JSON APIs for company submissions and XBRL data... Automated access
 * is permitted subject to fair-access controls, including a current maximum of 10 requests/second
 * and a declared User-Agent."). Roadmap item 2.1's first, narrowly-scoped slice: fetch a company's
 * recent filings and classify the most recent periodic financial statement filing (10-K or 10-Q).
 *
 * <p>Unlike UK Companies House (roadmap item 1.13, blocked on an API key decision), SEC EDGAR
 * requires no credential -- only a descriptive {@code User-Agent} identifying the requester, per
 * SEC's own fair-access policy -- so this connector is genuinely implementable without a human
 * decision.
 *
 * <p>The submissions response encodes {@code filings.recent} as parallel arrays (one array per
 * field, indexed by filing) rather than an array of filing objects -- a real quirk of this API,
 * not a simplification -- so parsing walks the arrays by index.
 */
@Component
public class SecEdgarClient {

    // SEC's fair-access policy requires a declared User-Agent containing a real contact address
    // (https://www.sec.gov/os/webmaster-faq#developers); a User-Agent without one is rejected with
    // HTTP 403, confirmed empirically against the live API while building this connector.
    private static final String USER_AGENT =
            "EWS Financial Intelligence Research Prototype contact@ewsfi-research.example.com";
    private static final Set<String> PERIODIC_STATEMENT_FORMS = Set.of("10-K", "10-Q");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SecEdgarClient(ObjectMapper objectMapper) {
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches CIK {@code cik}'s submissions and returns its most recent 10-K or 10-Q filing, if
     * any exists in the "recent" window SEC returns (older filings require paginated additional
     * files, not implemented here).
     */
    public Optional<SecFiling> fetchMostRecentPeriodicStatement(String cik) throws IOException, InterruptedException {
        String paddedCik = String.format("%010d", Long.parseLong(cik));
        URI uri = URI.create("https://data.sec.gov/submissions/CIK" + paddedCik + ".json");

        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException(
                    "SEC EDGAR returned HTTP " + response.statusCode() + " for CIK " + cik);
        }

        return parseMostRecentPeriodicStatement(cik, response.body());
    }

    Optional<SecFiling> parseMostRecentPeriodicStatement(String cik, String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String companyName = root.path("name").asText(null);
        JsonNode recent = root.path("filings").path("recent");

        JsonNode forms = recent.path("form");
        JsonNode filingDates = recent.path("filingDate");
        JsonNode reportDates = recent.path("reportDate");
        JsonNode accessionNumbers = recent.path("accessionNumber");
        JsonNode primaryDocuments = recent.path("primaryDocument");

        for (int i = 0; i < forms.size(); i++) {
            String form = forms.get(i).asText();
            if (PERIODIC_STATEMENT_FORMS.contains(form)) {
                return Optional.of(
                        new SecFiling(
                                cik,
                                companyName,
                                form,
                                filingDates.get(i).asText(),
                                reportDates.get(i).asText(),
                                accessionNumbers.get(i).asText(),
                                primaryDocuments.get(i).asText()));
            }
        }
        return Optional.empty();
    }
}
