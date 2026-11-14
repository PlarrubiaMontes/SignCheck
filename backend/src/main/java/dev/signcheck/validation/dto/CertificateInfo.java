package dev.signcheck.validation.dto;

import java.time.Instant;

/**
 * One certificate of the signer's chain (signing certificate first, trust anchor last).
 *
 * @param subject   subject distinguished name (RFC 2253)
 * @param issuer    issuer distinguished name (RFC 2253)
 * @param notBefore start of the validity period
 * @param notAfter  end of the validity period
 * @param trusted   whether this certificate is a trust anchor of the current trust mode
 */
public record CertificateInfo(String subject, String issuer, Instant notBefore, Instant notAfter, boolean trusted) {
}
