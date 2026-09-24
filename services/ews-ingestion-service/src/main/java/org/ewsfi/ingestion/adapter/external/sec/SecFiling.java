package org.ewsfi.ingestion.adapter.external.sec;

/**
 * A single SEC EDGAR filing, as classified from the {@code data.sec.gov/submissions} response by
 * {@link SecEdgarClient}. Mirrors the subset of fields
 * docs/research/us-uk-corporate-credit-data-source-landscape.md Section 2.1 names as the
 * "highest-value" observations: form type, filing/report dates, and the accession/document
 * identifiers needed to locate the filing.
 */
public record SecFiling(
        String cik,
        String companyName,
        String form,
        String filingDate,
        String reportDate,
        String accessionNumber,
        String primaryDocument) {

    public String primaryDocumentUrl() {
        String accessionNoDashes = accessionNumber.replace("-", "");
        String cikNoLeadingZeros = String.valueOf(Long.parseLong(cik));
        return "https://www.sec.gov/Archives/edgar/data/"
                + cikNoLeadingZeros
                + "/"
                + accessionNoDashes
                + "/"
                + primaryDocument;
    }
}
