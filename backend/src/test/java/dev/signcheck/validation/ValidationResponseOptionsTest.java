package dev.signcheck.validation;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signcheck.testpki.SignedSamples;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** Optional behaviour of {@code POST /api/v1/validations}: language of the reasons and raw DSS reports. */
class ValidationResponseOptionsTest extends ValidationApiTestSupport {

    @Test
    void shouldExplainTheReasonInEnglishByDefault() throws Exception {
        upload(SignedSamples.tamper(SignedSamples.padesBaselineB(pki.valid())), null, false)
                .andExpect(jsonPath("$.signatures[0].subIndication").value("HASH_FAILURE"))
                .andExpect(jsonPath("$.signatures[0].reason").value("The document was modified after it was signed."));
    }

    @Test
    void shouldExplainTheReasonInSpanishWhenRequested() throws Exception {
        upload(SignedSamples.tamper(SignedSamples.padesBaselineB(pki.valid())), "es-ES,es;q=0.9", false)
                .andExpect(jsonPath("$.signatures[0].reason").value("El documento se modificó después de firmarse."));
    }

    @Test
    void shouldFallBackToEnglishForUnsupportedLanguages() throws Exception {
        upload(SignedSamples.tamper(SignedSamples.padesBaselineB(pki.valid())), "de-DE", false)
                .andExpect(jsonPath("$.signatures[0].reason").value("The document was modified after it was signed."));
    }

    @Test
    void shouldGiveNoReasonForAValidSignature() throws Exception {
        upload(SignedSamples.padesBaselineB(pki.valid()), "es", false)
                .andExpect(jsonPath("$.signatures[0].verdict").value("VALID"))
                .andExpect(jsonPath("$.signatures[0].reason").doesNotExist());
    }

    @Test
    void shouldNotIncludeRawReportsUnlessRequested() throws Exception {
        upload(SignedSamples.padesBaselineB(pki.valid()), null, false)
                .andExpect(jsonPath("$.rawReports").doesNotExist());
    }

    @Test
    void shouldIncludeTheFourDssReportsAsXmlWhenRequested() throws Exception {
        upload(SignedSamples.padesBaselineB(pki.valid()), null, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawReports.simpleReport").value(containsString("SimpleReport")))
                .andExpect(jsonPath("$.rawReports.detailedReport").value(containsString("DetailedReport")))
                .andExpect(jsonPath("$.rawReports.diagnosticData").value(containsString("DiagnosticData")))
                .andExpect(jsonPath("$.rawReports.etsiValidationReport").value(containsString("ValidationReport")));
    }

    private ResultActions upload(byte[] signed, String acceptLanguage, boolean includeRawReports) throws Exception {
        var request = multipart("/api/v1/validations")
                .file(new MockMultipartFile("file", "signed.pdf", MediaType.APPLICATION_PDF_VALUE, signed))
                .param("includeRawReports", String.valueOf(includeRawReports));
        if (acceptLanguage != null) {
            request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }
}
