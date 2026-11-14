package dev.signcheck.validation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** {@code GET /api/v1/trust/status} in KEYSTORE mode (LOTL mode: see {@code LotlTrustModeTest}). */
class TrustStatusEndpointTest extends ValidationApiTestSupport {

    @Test
    void shouldReportTheKeystoreAnchorsAndNoTrustedLists() throws Exception {
        mockMvc.perform(get("/api/v1/trust/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("KEYSTORE"))
                .andExpect(jsonPath("$.loading").value(false))
                .andExpect(jsonPath("$.lastSuccessfulRefresh").doesNotExist())
                .andExpect(jsonPath("$.trustedListCount").value(0))
                .andExpect(jsonPath("$.trustedCertificateCount").value(1));
    }
}
