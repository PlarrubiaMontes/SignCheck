package dev.signcheck.validation.dto;

import java.time.Instant;

/**
 * Latest revocation data found for the signing certificate.
 *
 * @param status     {@code GOOD}, {@code REVOKED} or {@code UNKNOWN} (as stated by the CRL or OCSP response)
 * @param source     {@code CRL} or {@code OCSP}
 * @param producedAt when the CRL / OCSP response was produced
 */
public record RevocationInfo(String status, String source, Instant producedAt) {
}
