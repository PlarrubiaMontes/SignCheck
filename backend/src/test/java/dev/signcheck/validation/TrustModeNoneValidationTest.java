package dev.signcheck.validation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signcheck.testpki.SignedSamples;
import dev.signcheck.testpki.TestPki;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** With the application's own configuration (trust mode NONE) nothing can be trusted. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrustModeNoneValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReportIndeterminateForACorrectSignatureWhenNoTrustAnchorIsConfigured() throws Exception {
        byte[] signed = SignedSamples.padesBaselineB(TestPki.get().valid());
        var file = new MockMultipartFile("file", "signed.pdf", MediaType.APPLICATION_PDF_VALUE, signed);

        mockMvc.perform(multipart("/api/v1/validations").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trustMode").value("NONE"))
                .andExpect(jsonPath("$.signatures[0].verdict").value("INDETERMINATE"))
                .andExpect(jsonPath("$.signatures[0].subIndication").value("NO_CERTIFICATE_CHAIN_FOUND"));
    }
}
