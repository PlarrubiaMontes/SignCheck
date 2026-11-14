package dev.signcheck.trust;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signcheck.config.NetworkDataLoaders;
import dev.signcheck.testpki.OfflineNetwork;
import dev.signcheck.testpki.SignedSamples;
import dev.signcheck.testpki.TestPki;
import dev.signcheck.testpki.TestTrustedLists;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Security property of the LOTL loading: a LOTL signed by a certificate that was not announced in the
 * Official Journal must not add any trust anchor, even if the national list it points to is fine.
 */
@Import(LotlWithUnknownSignerTest.ForgedLotl.class)
class LotlWithUnknownSignerTest extends LotlTrustModeTestSupport {

    @Test
    void shouldNotTrustAnythingFromALotlSignedByAnUnannouncedCertificate() throws Exception {
        mockMvc.perform(get("/api/v1/trust/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loading").value(false))
                .andExpect(jsonPath("$.lastSuccessfulRefresh").doesNotExist())
                .andExpect(jsonPath("$.trustedCertificateCount").value(0));
    }

    @Test
    void shouldReportIndeterminateBecauseNoTrustAnchorWasLoaded() throws Exception {
        validate(SignedSamples.padesBaselineB(pki.valid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatures[0].verdict").value("INDETERMINATE"))
                .andExpect(jsonPath("$.signatures[0].subIndication").value("NO_CERTIFICATE_CHAIN_FOUND"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ForgedLotl {

        @Bean
        @Primary
        NetworkDataLoaders forgedLotlNetwork() {
            TestPki pki = TestPki.get();
            return OfflineNetwork.loaders(Map.of(
                    // signed by the TL signer, which is not in the "Official Journal" keystore
                    TestTrustedLists.LOTL_URL, TestTrustedLists.lotl(pki.tlSigner()),
                    TestTrustedLists.TL_URL, TestTrustedLists.tl(6, pki.tlSigner())));
        }
    }
}
