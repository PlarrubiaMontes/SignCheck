package dev.signcheck.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signcheck.testpki.SampleDocuments;
import dev.signcheck.testpki.SignedSamples;
import dev.signcheck.testpki.TestPki;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Defensive behaviour of the API: request shape checks, CORS restricted to configured origins and no
 * document content or personal data in the logs (CLAUDE.md, rule 8).
 */
@SpringBootTest(properties = "signcheck.cors.allowed-origins=http://localhost:4200")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class HardeningTest {

    private static final String URL = "/api/v1/validations";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectANonMultipartRequestWithACode() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void shouldRejectAnUnsupportedMethodWithACode() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void shouldAllowCrossOriginCallsFromTheConfiguredOrigin() throws Exception {
        mockMvc.perform(options(URL)
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void shouldRejectCrossOriginCallsFromOtherOrigins() throws Exception {
        mockMvc.perform(options(URL)
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void shouldLogOnlyMetadataOfTheValidatedDocument(CapturedOutput output) throws Exception {
        String fileName = "confidential-contract-of-jane-doe.pdf";
        byte[] signed = SignedSamples.padesBaselineB(TestPki.get().valid());

        mockMvc.perform(multipart(URL).file(
                        new MockMultipartFile("file", fileName, MediaType.APPLICATION_PDF_VALUE, signed)))
                .andExpect(status().isOk());

        assertThat(output.getAll())
                .contains("Validated document: size=" + signed.length + " bytes")
                .doesNotContain(fileName)
                .doesNotContain("TEST SIGNER Valid")
                .doesNotContain(SampleDocuments.CONTENT_MARKER);
    }
}
