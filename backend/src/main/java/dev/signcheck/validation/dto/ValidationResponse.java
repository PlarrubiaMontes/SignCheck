package dev.signcheck.validation.dto;

import dev.signcheck.trust.TrustMode;
import java.time.Instant;
import java.util.List;

/**
 * Body of {@code POST /api/v1/validations}. Our own type: DSS classes never reach the API
 * (see {@code ValidationReportMapper}).
 *
 * @param fileName       name of the uploaded file, as sent by the client
 * @param detectedFormat signature format detected from the content (not from the extension)
 * @param validationTime time the validation was performed
 * @param trustMode      trust anchors in use, which conditions whether a signature can be {@code VALID}
 * @param summary        document-level summary
 * @param signatures     one entry per signature, in document order
 * @param rawReports     DSS XML reports, only when requested ({@code includeRawReports=true}), else {@code null}
 */
public record ValidationResponse(
        String fileName,
        String detectedFormat,
        Instant validationTime,
        TrustMode trustMode,
        ValidationSummary summary,
        List<SignatureResult> signatures,
        RawReports rawReports) {

    public ValidationResponse {
        signatures = List.copyOf(signatures);
    }
}
