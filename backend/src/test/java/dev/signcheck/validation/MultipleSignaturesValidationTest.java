package dev.signcheck.validation;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signcheck.testpki.SampleDocuments;
import dev.signcheck.testpki.SignedSamples;
import org.junit.jupiter.api.Test;

/**
 * A PDF can hold several signatures, each added in an incremental update that leaves the previous
 * revision untouched. Each signature is validated on its own and the summary aggregates them.
 */
class MultipleSignaturesValidationTest extends ValidationApiTestSupport {

    @Test
    void shouldReportValidWhenEverySignatureIsValid() throws Exception {
        validate("signed.pdf", twoValidSignatures())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.signatureCount").value(2))
                .andExpect(jsonPath("$.summary.validCount").value(2))
                .andExpect(jsonPath("$.summary.overallVerdict").value("VALID"));
    }

    @Test
    void shouldReportEachSignatureSeparatelyWhenOneIsBroken() throws Exception {
        byte[] secondBroken = SignedSamples.corruptSignatureValue(twoValidSignatures(), 1);

        validate("signed.pdf", secondBroken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatures", hasSize(2)))
                .andExpect(jsonPath("$.signatures[0].verdict").value("VALID"))
                .andExpect(jsonPath("$.signatures[1].verdict").value("INVALID"))
                .andExpect(jsonPath("$.signatures[1].subIndication").value("SIG_CRYPTO_FAILURE"))
                .andExpect(jsonPath("$.summary.signatureCount").value(2))
                .andExpect(jsonPath("$.summary.validCount").value(1))
                .andExpect(jsonPath("$.summary.overallVerdict").value("INVALID"));
    }

    @Test
    void shouldReportIndeterminateWhenOneSignerIsNotTrusted() throws Exception {
        byte[] first = SignedSamples.padesBaselineB(pki.valid());
        byte[] second = SignedSamples.padesBaselineB(first, pki.untrusted(), pki.now());

        validate("signed.pdf", second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatures[0].verdict").value("VALID"))
                .andExpect(jsonPath("$.signatures[1].verdict").value("INDETERMINATE"))
                .andExpect(jsonPath("$.summary.validCount").value(1))
                .andExpect(jsonPath("$.summary.overallVerdict").value("INDETERMINATE"));
    }

    private byte[] twoValidSignatures() {
        byte[] first = SignedSamples.padesBaselineB(SampleDocuments.pdf(), pki.valid(), pki.now());
        return SignedSamples.padesBaselineB(first, pki.valid(), pki.now());
    }
}
