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
 * A correctly signed LOTL (v6) pointing to a correctly signed national TL (v5): the TL's CA becomes a
 * trust anchor, so signatures from the test PKI are {@code VALID}. Also covers both TL versions.
 */
@Import(LotlTrustModeTest.ValidLists.class)
class LotlTrustModeTest extends LotlTrustModeTestSupport {

    @Test
    void shouldLoadTheTrustedListsAnnouncedByAValidlySignedLotl() throws Exception {
        mockMvc.perform(get("/api/v1/trust/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LOTL"))
                .andExpect(jsonPath("$.loading").value(false))
                .andExpect(jsonPath("$.lastSuccessfulRefresh").isNotEmpty())
                .andExpect(jsonPath("$.trustedListCount").value(1))
                .andExpect(jsonPath("$.trustedCertificateCount").value(1));
    }

    @Test
    void shouldReportValidForASignerIssuedByATrustedListService() throws Exception {
        validate(SignedSamples.padesBaselineB(pki.valid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trustMode").value("LOTL"))
                .andExpect(jsonPath("$.signatures[0].verdict").value("VALID"));
    }

    @Test
    void shouldStillReportIndeterminateForASignerOutsideTheTrustedLists() throws Exception {
        validate(SignedSamples.padesBaselineB(pki.untrusted()))
                .andExpect(jsonPath("$.signatures[0].subIndication").value("NO_CERTIFICATE_CHAIN_FOUND"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ValidLists {

        @Bean
        @Primary
        NetworkDataLoaders validTrustedListsNetwork() {
            TestPki pki = TestPki.get();
            return OfflineNetwork.loaders(Map.of(
                    TestTrustedLists.LOTL_URL, TestTrustedLists.lotl(pki.lotlSigner()),
                    TestTrustedLists.TL_URL, TestTrustedLists.tl(5, pki.tlSigner())));
        }
    }
}
