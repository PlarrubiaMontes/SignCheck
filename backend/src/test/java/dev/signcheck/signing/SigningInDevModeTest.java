package dev.signcheck.signing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signcheck.testpki.SampleDocuments;
import dev.signcheck.testpki.SignedSamples;
import dev.signcheck.testpki.TestPki;
import dev.signcheck.testpki.TestPkiFiles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The local-development set-up (trust mode NONE, signing enabled, no TSA, no network): the demo CA is the
 * only trust anchor and its CRL is answered locally, so a document signed by the demo is VALID by hand.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SigningInDevModeTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void devLikeSetUp(DynamicPropertyRegistry registry) {
        registry.add("signcheck.trust.mode", () -> "NONE");
        registry.add("signcheck.signing.enabled", () -> "true");
        registry.add("signcheck.signing.demo-ca-dir", () -> TestPkiFiles.tempDirectory("signcheck-demo-ca"));
    }

    @Test
    void shouldValidateADemoSignatureAsValidWithoutAnyNetwork() throws Exception {
        byte[] signed = mockMvc.perform(multipart("/api/v1/signatures")
                        .file(new MockMultipartFile("file", "a.pdf", MediaType.APPLICATION_PDF_VALUE, SampleDocuments.pdf()))
                        .param("format", "PADES"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        mockMvc.perform(multipart("/api/v1/validations")
                        .file(new MockMultipartFile("file", "a-signed.pdf", MediaType.APPLICATION_PDF_VALUE, signed)))
                .andExpect(jsonPath("$.trustMode").value("NONE"))
                .andExpect(jsonPath("$.signatures[0].verdict").value("VALID"));
    }

    @Test
    void shouldStillNotTrustOtherSigners() throws Exception {
        mockMvc.perform(multipart("/api/v1/validations").file(new MockMultipartFile("file", "x.pdf",
                        MediaType.APPLICATION_PDF_VALUE, SignedSamples.padesBaselineB(TestPki.get().valid()))))
                .andExpect(jsonPath("$.signatures[0].subIndication").value("NO_CERTIFICATE_CHAIN_FOUND"));
    }

    @Test
    void shouldOfferOnlyLevelBWithoutATimestampingAuthority() throws Exception {
        mockMvc.perform(get("/api/v1/signatures/options"))
                .andExpect(jsonPath("$.levels.length()").value(1));
        mockMvc.perform(multipart("/api/v1/signatures")
                        .file(new MockMultipartFile("file", "a.pdf", MediaType.APPLICATION_PDF_VALUE, SampleDocuments.pdf()))
                        .param("format", "PADES").param("level", "T"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNING_REQUEST"));
    }
}
