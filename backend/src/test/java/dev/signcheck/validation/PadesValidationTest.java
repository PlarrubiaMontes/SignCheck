package dev.signcheck.validation;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signcheck.testpki.SignedSamples;
import org.junit.jupiter.api.Test;

/** PAdES (PDF signatures) through the REST API, trusting the test PKI root (KEYSTORE mode). */
class PadesValidationTest extends ValidationApiTestSupport {

    @Test
    void shouldReportValidWhenSignedByATrustedSigner() throws Exception {
        validate("signed.pdf", SignedSamples.padesBaselineB(pki.valid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("signed.pdf"))
                .andExpect(jsonPath("$.detectedFormat").value("PAdES"))
                .andExpect(jsonPath("$.trustMode").value("KEYSTORE"))
                .andExpect(jsonPath("$.summary.signatureCount").value(1))
                .andExpect(jsonPath("$.summary.validCount").value(1))
                .andExpect(jsonPath("$.summary.overallVerdict").value("VALID"))
                .andExpect(jsonPath("$.signatures", hasSize(1)))
                .andExpect(jsonPath("$.signatures[0].id").value(startsWith("S-")))
                .andExpect(jsonPath("$.signatures[0].verdict").value("VALID"))
                .andExpect(jsonPath("$.signatures[0].subIndication").doesNotExist())
                .andExpect(jsonPath("$.signatures[0].format").value("PAdES"))
                .andExpect(jsonPath("$.signatures[0].level").value("PAdES-BASELINE-B"))
                .andExpect(jsonPath("$.signatures[0].signer.commonName").value("TEST SIGNER Valid"))
                .andExpect(jsonPath("$.signatures[0].signer.organization").value("SignCheck Test PKI"))
                .andExpect(jsonPath("$.signatures[0].signer.country").value("ES"))
                .andExpect(jsonPath("$.signatures[0].signer.certificateSerialNumber")
                        .value(pki.valid().certificate().getSerialNumber().toString(16).toUpperCase()))
                .andExpect(jsonPath("$.signatures[0].issuer").value(startsWith("CN=SignCheck TEST Intermediate CA")))
                .andExpect(jsonPath("$.signatures[0].claimedSigningTime").value(pki.now().toString()))
                .andExpect(jsonPath("$.signatures[0].errors", hasSize(0)));
    }

    @Test
    void shouldReportInvalidWhenDocumentWasModifiedAfterSigning() throws Exception {
        validate("signed.pdf", SignedSamples.tamper(SignedSamples.padesBaselineB(pki.valid())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.overallVerdict").value("INVALID"))
                .andExpect(jsonPath("$.signatures[0].verdict").value("INVALID"))
                .andExpect(jsonPath("$.signatures[0].subIndication").value("HASH_FAILURE"));
    }

    @Test
    void shouldReportIndeterminateWhenSignerChainsToAnUntrustedRoot() throws Exception {
        validate("signed.pdf", SignedSamples.padesBaselineB(pki.untrusted()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.overallVerdict").value("INDETERMINATE"))
                .andExpect(jsonPath("$.signatures[0].verdict").value("INDETERMINATE"))
                .andExpect(jsonPath("$.signatures[0].subIndication").value("NO_CERTIFICATE_CHAIN_FOUND"));
    }
}
