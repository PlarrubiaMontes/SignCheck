package dev.signcheck.validation;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signcheck.testpki.SignedSamples;
import org.junit.jupiter.api.Test;

/** CAdES (CMS signatures): attached packaging. Detached: see {@link DetachedValidationTest}. */
class CadesValidationTest extends ValidationApiTestSupport {

    @Test
    void shouldReportValidForAnAttachedSignatureByATrustedSigner() throws Exception {
        validate("signed.p7m", SignedSamples.cadesAttached(pki.valid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectedFormat").value("CAdES"))
                .andExpect(jsonPath("$.signatures[0].verdict").value("VALID"))
                .andExpect(jsonPath("$.signatures[0].level").value("CAdES-BASELINE-B"))
                .andExpect(jsonPath("$.signatures[0].signer.commonName").value("TEST SIGNER Valid"));
    }

    @Test
    void shouldReportInvalidWhenAttachedContentWasModifiedAfterSigning() throws Exception {
        validate("signed.p7m", SignedSamples.tamper(SignedSamples.cadesAttached(pki.valid())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatures[0].verdict").value("INVALID"))
                .andExpect(jsonPath("$.signatures[0].subIndication").value("HASH_FAILURE"));
    }
}
