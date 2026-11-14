package dev.signcheck.validation.dto;

import java.time.Instant;
import java.util.List;

/**
 * Validation result of one signature.
 *
 * @param id                 identifier DSS assigns to the signature (stable for the same document)
 * @param verdict            main indication
 * @param subIndication      ETSI EN 319 102-1 sub-indication explaining a non-valid verdict
 *                           (e.g. {@code HASH_FAILURE}); {@code null} when valid
 * @param reason             the sub-indication explained in plain language, in the request's language
 *                           ({@code Accept-Language}: English or Spanish); {@code null} when valid
 * @param format             signature format: {@code PAdES}, {@code XAdES}, {@code CAdES}...
 * @param level              signature level, e.g. {@code PAdES-BASELINE-B}
 * @param qualification      eIDAS qualification as determined by DSS from the Trusted Lists
 *                           (e.g. {@code QESig}, {@code AdES}, {@code N/A} when it cannot be determined)
 * @param signer             signer identity, {@code null} if the signing certificate was not found
 * @param issuer             distinguished name of the signing certificate's issuer
 * @param claimedSigningTime signing time written by the signer (not proven)
 * @param bestSignatureTime  earliest time the signature is proven to exist (timestamps); without
 *                           timestamps it is the validation time
 * @param certificateChain   signing certificate and its issuers, up to the trust anchor when found
 * @param revocation         latest revocation data of the signing certificate; {@code null} if none was found
 * @param timestamps         timestamps covering the signature
 * @param warnings           validation warnings
 * @param errors             validation errors
 */
public record SignatureResult(
        String id,
        Verdict verdict,
        String subIndication,
        String reason,
        String format,
        String level,
        String qualification,
        SignerInfo signer,
        String issuer,
        Instant claimedSigningTime,
        Instant bestSignatureTime,
        List<CertificateInfo> certificateChain,
        RevocationInfo revocation,
        List<TimestampInfo> timestamps,
        List<String> warnings,
        List<String> errors) {

    public SignatureResult {
        certificateChain = List.copyOf(certificateChain);
        timestamps = List.copyOf(timestamps);
        warnings = List.copyOf(warnings);
        errors = List.copyOf(errors);
    }
}
