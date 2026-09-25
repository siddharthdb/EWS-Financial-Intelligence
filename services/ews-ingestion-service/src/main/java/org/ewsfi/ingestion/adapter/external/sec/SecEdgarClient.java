package org.ewsfi.ingestion.adapter.external.sec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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

    /**
     * Balance-sheet ("instant", not duration) us-gaap XBRL concepts extracted per filing --
     * deliberately a small, high-value subset (roadmap item 2.8's remaining P10-P34 contracts need
     * real financial figures, not full XBRL taxonomy coverage) rather than every concept SEC
     * reports, mirroring how item 1.14 deliberately scoped `wc_utilization_ratio` to the baseline
     * sanctioned limit rather than every capacity variant.
     */
    static final Set<String> BALANCE_SHEET_CONCEPTS =
            Set.of(
                    "Assets",
                    "Liabilities",
                    "StockholdersEquity",
                    "AssetsCurrent",
                    "LiabilitiesCurrent",
                    "AccountsReceivableNetCurrent",
                    "InventoryNet");

    /**
     * Duration (income-statement/cash-flow, not instant) us-gaap XBRL concepts, for P13
     * OPERATING_PROFIT_MATERIAL_DECLINE / P14 OPERATING_CASH_FLOW_NEGATIVE / P15
     * RECEIVABLE_DAYS_DERIORATION. Duration concepts need the extra period-disambiguation
     * {@link #parseXbrlFactsForAccession} applies (see its javadoc) that
     * {@link #BALANCE_SHEET_CONCEPTS}' instant concepts don't -- a single accession number reports
     * multiple overlapping/comparative periods for these (e.g. a 10-Q's current quarter, current
     * year-to-date, and prior-year comparatives for the same concept), unlike a balance sheet's
     * single "as of" date.
     *
     * <p>Revenue is two concepts, not one -- a real XBRL taxonomy migration, confirmed empirically:
     * large filers (including Apple) stopped tagging {@code Revenues} around fiscal 2018 in favor of
     * the more specific ASC 606 concept {@code RevenueFromContractWithCustomerExcludingAssessedTax}.
     * Relying on {@code Revenues} alone would silently return no revenue figure at all for any
     * filer that migrated -- not an error, just a quietly missing feature -- so both are extracted
     * and {@code ReceivableDaysFeatureTopology} prefers the newer concept, falling back to the
     * older one only if the newer one is absent.
     *
     * <p>{@code CostOfGoodsAndServicesSold} (not {@code CostOfRevenue}, which Apple does not tag --
     * another real concept-naming variation, confirmed empirically) is the denominator for P16
     * INVENTORY_DAYS_DERIORATION's {@code inventory_days} feature.
     *
     * <p>{@code NetIncomeLoss} feeds {@code net_income} / NET_LOSS_EMERGENCE
     * (docs/architecture/04-signal-taxonomy.md Section 4: "income statement", method R) -- a
     * taxonomy-defined signal outside the curated P01-P34 priority contract list, part of roadmap
     * item 2.3's remaining scope ("financial-statement-based signals in 04-signal-taxonomy.md
     * Section 4 remain unimplemented").
     */
    static final Set<String> DURATION_CONCEPTS =
            Set.of(
                    "OperatingIncomeLoss",
                    "NetCashProvidedByUsedInOperatingActivities",
                    "RevenueFromContractWithCustomerExcludingAssessedTax",
                    "Revenues",
                    "CostOfGoodsAndServicesSold",
                    "NetIncomeLoss");

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

    /**
     * A filing's extracted XBRL facts, plus the discrete reporting period's length in days
     * ({@code periodDays}, {@code null} if no duration concept resolved) -- needed to normalize a
     * duration-concept-derived ratio like {@code receivable_days} to a period-independent figure
     * (docs/architecture/02d-phase1-feature-catalogue.md Section 7: "normalized to period days").
     * Every duration concept that resolves for one filing shares the same discrete period by
     * construction ({@link #selectFactForFiling}'s end-date-match + shortest-duration selection), so
     * one {@code periodDays} value is valid for all of them.
     */
    record XbrlFilingFacts(Map<String, Long> values, Long periodDays) {
    }

    /**
     * Fetches CIK {@code cik}'s full XBRL company facts and returns the {@link #BALANCE_SHEET_CONCEPTS}
     * and {@link #DURATION_CONCEPTS} values reported specifically for the filing identified by
     * {@code accessionNumber} and {@code reportDate} -- both already returned by
     * {@link #fetchMostRecentPeriodicStatement}, so the caller ties facts back to a filing it
     * already knows about rather than guessing which period applies. Returns an empty map if the
     * filing has no matching XBRL facts (e.g. a filer whose facts predate SEC's XBRL company-facts
     * coverage, or a concept genuinely not reported that period).
     */
    public XbrlFilingFacts fetchXbrlFactsForFiling(String cik, String accessionNumber, String reportDate)
            throws IOException, InterruptedException {
        String paddedCik = String.format("%010d", Long.parseLong(cik));
        URI uri = URI.create("https://data.sec.gov/api/xbrl/companyfacts/CIK" + paddedCik + ".json");

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
                    "SEC EDGAR returned HTTP " + response.statusCode() + " for CIK " + cik + " company facts");
        }

        return parseXbrlFactsForAccession(response.body(), accessionNumber, reportDate);
    }

    /**
     * Parses the {@code companyfacts} response, keeping only {@link #BALANCE_SHEET_CONCEPTS}/
     * {@link #DURATION_CONCEPTS} values reported under the given accession number.
     *
     * <p>Each concept's {@code units.USD} array holds one entry per reporting period the filer has
     * ever disclosed that concept in, spanning many different filings -- {@code accn} is the only
     * field that reliably identifies which entries belong to one specific filing (rather than by
     * date alone, which can collide across amended/duplicate filings), so it is the primary filter.
     * For a duration concept, however, a single accession number can still report <em>several</em>
     * overlapping/comparative period entries for the same concept (a 10-Q typically reports the
     * current quarter, the current year-to-date, and both prior-year comparatives under one
     * accession) -- confirmed empirically against real SEC data while building this. Two further
     * filters disambiguate which one entry is "this filing's own reported figure": (1) the entry's
     * {@code end} date must match {@code reportDate} (the filing's own "as of"/period-end date,
     * ruling out prior-year comparatives whose {@code end} differs); (2) among any remaining ties
     * (e.g. a 10-Q's current-quarter and current-year-to-date figures both end on the same date),
     * the entry with the <em>shortest</em> {@code end - start} duration is kept -- the discrete
     * period this specific filing newly reports, not a cumulative year-to-date figure. Instant
     * concepts have no {@code start} field (duration is treated as zero), so this reduces to their
     * original single-match behavior; they are not affected by this disambiguation in practice.
     */
    XbrlFilingFacts parseXbrlFactsForAccession(String responseBody, String accessionNumber, String reportDate)
            throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode usGaap = root.path("facts").path("us-gaap");

        Set<String> allConcepts = new LinkedHashSet<>();
        allConcepts.addAll(BALANCE_SHEET_CONCEPTS);
        allConcepts.addAll(DURATION_CONCEPTS);

        Map<String, Long> facts = new LinkedHashMap<>();
        Long periodDays = null;
        for (String concept : allConcepts) {
            JsonNode entries = usGaap.path(concept).path("units").path("USD");
            Optional<JsonNode> selected = selectFactForFiling(entries, accessionNumber, reportDate);
            if (selected.isPresent()) {
                JsonNode entry = selected.get();
                facts.put(concept, entry.path("val").asLong());
                if (periodDays == null && DURATION_CONCEPTS.contains(concept)) {
                    long days = durationDays(entry);
                    if (days > 0) {
                        periodDays = days;
                    }
                }
            }
        }
        return new XbrlFilingFacts(facts, periodDays);
    }

    private Optional<JsonNode> selectFactForFiling(JsonNode entries, String accessionNumber, String reportDate) {
        JsonNode best = null;
        long bestDurationDays = Long.MAX_VALUE;
        for (JsonNode entry : entries) {
            if (!accessionNumber.equals(entry.path("accn").asText(null))) {
                continue;
            }
            String end = entry.path("end").asText(null);
            if (end == null || !end.equals(reportDate)) {
                continue;
            }
            long durationDays = durationDays(entry);
            if (durationDays < bestDurationDays) {
                best = entry;
                bestDurationDays = durationDays;
            }
        }
        return Optional.ofNullable(best);
    }

    private long durationDays(JsonNode entry) {
        String start = entry.path("start").asText(null);
        String end = entry.path("end").asText(null);
        if (start == null || end == null) {
            return 0L;
        }
        try {
            return ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end));
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
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
