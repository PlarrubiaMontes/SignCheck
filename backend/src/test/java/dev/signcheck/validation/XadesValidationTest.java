package dev.signcheck.validation;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signcheck.testpki.SignedSamples;
import org.junit.jupiter.api.Test;

/** XAdES (XML signatures): enveloped and enveloping packaging. Detached: see {@link DetachedValidationTest}. */
class XadesValidationTest extends ValidationApiTestSupport {

    @Test
    void shouldReportValidForAnEnvelopedSignatureByATrustedSigner() throws Exception {
        validate("signed.xml", SignedSamples.xadesEnveloped(pki.valid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectedFormat").value("XAdES"))
                .andExpect(jsonPath("$.signatures[0].verdict").value("VALID"))
                .andExpect(jsonPath("$.signatures[0].level").value("XAdES-BASELINE-B"))
                .andExpect(jsonPath("$.signatures[0].signer.commonName").value("TEST SIGNER Valid"));
    }

    @Test
    void shouldReportInvalidWhenEnvelopedXmlWasModifiedAfterSigning() throws Exception {
        validate("signed.xml", SignedSamples.tamper(SignedSamples.xadesEnveloped(pki.valid())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatures[0].verdict").value("INVALID"))
                .andExpect(jsonPath("$.signatures[0].subIndication").value("HASH_FAILURE"));
    }

    @Test
    void shouldReportValidForAnEnvelopingSignatureByATrustedSigner() throws Exception {
        validate("signed.xml", SignedSamples.xadesEnveloping(pki.valid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectedFormat").value("XAdES"))
                .andExpect(jsonPath("$.signatures[0].verdict").value("VALID"));
    }

    @Test
    void shouldReportInvalidWhenEnvelopedContentOfAnEnvelopingSignatureWasModified() throws Exception {
        validate("signed.xml", SignedSamples.tamper(SignedSamples.xadesEnveloping(pki.valid())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatures[0].verdict").value("INVALID"))
                .andExpect(jsonPath("$.signatures[0].subIndication").value("HASH_FAILURE"));
    }
}
