package dev.signcheck.validation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import dev.signcheck.testpki.OfflineNetworkConfig;
import dev.signcheck.testpki.TestPki;
import dev.signcheck.testpki.TestPkiFiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Base class for end-to-end validation tests through {@code POST /api/v1/validations}.
 *
 * <p>The application runs with its real {@code KEYSTORE} trust mode: a generated PKCS#12 holding the
 * {@link TestPki} root is the trust anchor, and CRLs are fetched through the production revocation sources
 * from an in-memory network ({@link OfflineNetworkConfig}). All subclasses share one Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OfflineNetworkConfig.class)
abstract class ValidationApiTestSupport {

    protected final TestPki pki = TestPki.get();

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void trustTheTestPkiRoot(DynamicPropertyRegistry registry) {
        registry.add("signcheck.trust.mode", () -> "KEYSTORE");
        registry.add("signcheck.trust.keystore.path",
                () -> TestPkiFiles.trustStore("test-root", TestPki.get().root().certificate()));
        registry.add("signcheck.trust.keystore.password", () -> TestPkiFiles.PASSWORD);
    }

    /** Uploads a signed file, optionally with original documents for detached signatures. */
    protected ResultActions validate(String fileName, byte[] signed, MockMultipartFile... originals) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/validations")
                .file(new MockMultipartFile("file", fileName, MediaType.APPLICATION_OCTET_STREAM_VALUE, signed));
        for (MockMultipartFile original : originals) {
            request.file(original);
        }
        return mockMvc.perform(request);
    }

    /** An {@code originals} part. */
    protected static MockMultipartFile original(String fileName, byte[] content) {
        return new MockMultipartFile("originals", fileName, MediaType.APPLICATION_OCTET_STREAM_VALUE, content);
    }
}
