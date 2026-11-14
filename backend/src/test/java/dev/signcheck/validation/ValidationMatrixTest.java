package dev.signcheck.validation;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signcheck.testpki.SampleDocuments;
import dev.signcheck.testpki.SignedSamples;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Certificate-related rows of the validation matrix (ARCHITECTURE §5). Format, tampering, detached
 * and multi-signature rows live in the format-specific tests; error rows in {@code ValidationErrorsTest}.
 *
 * <p>All signatures are PAdES-BASELINE-B: they carry no timestamp, so DSS has no <em>proof of
 * existence</em> (POE) of the signature before the validation time. That is why problems with the
 * certificate end in {@code INDETERMINATE} rather than {@code INVALID}: a timestamp proving the signature
 * was made while the certificate was still fine could change the outcome (EN 319 102-1).
 *
 * <p>The sub-indications asserted here are the ones DSS 6.5.1 returns with its default policy; each
 * test explains why, since several differ from the "obvious" ETSI name.
 */
class ValidationMatrixTest extends ValidationApiTestSupport {

    @Test
    void shouldReportIndeterminateWhenSignerCertificateHasExpired() throws Exception {
        // Claimed signing time inside the validity period; without a timestamp it cannot be proven.
        // Our CRL has no ExpiredCertsOnCRL extension, so it says nothing about certificates that already
        // expired: DSS finds no acceptable revocation data and the chain check is inconclusive.
        Instant whileStillValid = pki.expired().certificate().getNotAfter().toInstant().minus(Duration.ofDays(30));
        byte[] signed = SignedSamples.padesBaselineB(SampleDocuments.pdf(), pki.expired(), whileStillValid);

        expect(signed, "INDETERMINATE", "CERTIFICATE_CHAIN_GENERAL_FAILURE");
    }

    @Test
    void shouldReportIndeterminateWhenSignerCertificateIsNotYetValid() throws Exception {
        // The CRL was issued before the certificate existed, so it cannot vouch for it: no acceptable
        // revocation data. DSS also reports that the signing time is outside the validity range.
        expect(SignedSamples.padesBaselineB(pki.notYetValid()), "INDETERMINATE", "CERTIFICATE_CHAIN_GENERAL_FAILURE");
    }

    @Test
    void shouldReportIndeterminateWhenSignerCertificateWasRevoked() throws Exception {
        // Revoked before validation time and no POE of the signature before the revocation
        expect(SignedSamples.padesBaselineB(pki.revoked()), "INDETERMINATE", "REVOKED_NO_POE");
    }

    @Test
    void shouldOnlyWarnWhenSignerCertificateDoesNotAllowSigning() throws Exception {
        // DSS's default policy checks the signing certificate's key usage at WARN level: the signature is
        // reported VALID with a warning. A stricter custom policy could turn it into a failure.
        validate("signed.pdf", SignedSamples.padesBaselineB(pki.wrongKeyUsage()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatures[0].verdict").value("VALID"))
                .andExpect(jsonPath("$.signatures[0].warnings", hasItem(containsString("key-usage"))));
    }

    @Test
    void shouldReportIndeterminateWhenSignerChainsToAnUntrustedRoot() throws Exception {
        expect(SignedSamples.padesBaselineB(pki.untrusted()), "INDETERMINATE", "NO_CERTIFICATE_CHAIN_FOUND");
    }

    private void expect(byte[] signed, String verdict, String subIndication) throws Exception {
        validate("signed.pdf", signed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatures[0].verdict").value(verdict))
                .andExpect(jsonPath("$.signatures[0].subIndication").value(subIndication))
                .andExpect(jsonPath("$.summary.overallVerdict").value(verdict));
    }
}
