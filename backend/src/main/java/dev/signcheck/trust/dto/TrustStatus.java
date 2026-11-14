package dev.signcheck.trust.dto;

import dev.signcheck.trust.TrustMode;
import java.time.Instant;

/**
 * Body of {@code GET /api/v1/trust/status}.
 *
 * @param mode                    trust mode in use
 * @param loading                 {@code true} while the Trusted Lists are being loaded for the first time
 * @param lastSuccessfulRefresh   last download of a validly signed LOTL; {@code null} if never or not in LOTL mode
 * @param trustedListCount        national Trusted Lists loaded with a valid signature (LOTL mode only)
 * @param trustedCertificateCount trust anchors currently known
 */
public record TrustStatus(
        TrustMode mode,
        boolean loading,
        Instant lastSuccessfulRefresh,
        int trustedListCount,
        int trustedCertificateCount) {
}
