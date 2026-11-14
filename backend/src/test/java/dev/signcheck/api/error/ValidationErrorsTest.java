package dev.signcheck.api.error;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signcheck.testpki.SampleDocuments;
import dev.signcheck.validation.DocumentValidatorFactory;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Every error of {@code POST /api/v1/validations} is a ProblemDetail with a stable {@code code}. */
@SpringBootTest(properties = "signcheck.upload.max-file-size=4KB")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ValidationErrorsTest {

    private static final String URL = "/api/v1/validations";

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private DocumentValidatorFactory validatorFactory;

    @Test
    void shouldRejectARequestWithoutFilePart() throws Exception {
        var other = new MockMultipartFile("something", "x.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[] {1});

        expectProblem(mockMvc.perform(multipart(URL).file(other)), 400, "NO_FILE");
    }

    @Test
    void shouldRejectAnEmptyFile() throws Exception {
        expectProblem(upload(new byte[0]), 400, "NO_FILE");
    }

    @Test
    void shouldRejectAFileOverTheConfiguredLimit() throws Exception {
        expectProblem(upload(randomBytes(5 * 1024)), 413, "FILE_TOO_LARGE")
                .andExpect(jsonPath("$.detail").value(containsString("4 KB")));
    }

    @Test
    void shouldMapTheContainerUploadLimitToFileTooLarge() throws Exception {
        doThrow(new MaxUploadSizeExceededException(4096)).when(validatorFactory).create(any());

        expectProblem(upload(SampleDocuments.pdf()), 413, "FILE_TOO_LARGE");
    }

    @Test
    void shouldReportNoSignatureFoundForAnUnsignedPdf() throws Exception {
        expectProblem(upload(SampleDocuments.pdf()), 422, "NO_SIGNATURE_FOUND");
    }

    @Test
    void shouldReportUnsupportedFormatForRandomBytes() throws Exception {
        expectProblem(upload(randomBytes(512)), 422, "UNSUPPORTED_FORMAT");
    }

    @Test
    void shouldHideInternalDetailsOfUnexpectedFailures() throws Exception {
        doThrow(new IllegalStateException("secret internal detail")).when(validatorFactory).create(any());

        expectProblem(upload(SampleDocuments.pdf()), 500, "VALIDATION_ERROR")
                .andExpect(content().string(not(containsString("secret internal detail"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    private ResultActions upload(byte[] bytes) throws Exception {
        var file = new MockMultipartFile("file", "upload.bin", MediaType.APPLICATION_OCTET_STREAM_VALUE, bytes);
        return mockMvc.perform(multipart(URL).file(file));
    }

    private static ResultActions expectProblem(ResultActions result, int status, String code) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.code").value(code));
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new Random(42).nextBytes(bytes);
        return bytes;
    }
}
