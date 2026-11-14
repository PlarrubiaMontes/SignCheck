package dev.signcheck.trust;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import dev.signcheck.testpki.TestPki;
import dev.signcheck.testpki.TestPkiFiles;
import dev.signcheck.testpki.TestTrustedLists;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Runs the application in {@code LOTL} mode against the generated {@link TestTrustedLists}, served from
 * memory by the subclass's {@code NetworkDataLoaders}. The LOTL signer of the test PKI plays the role of
 * the certificates announced in the Official Journal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class LotlTrustModeTestSupport {

    protected final TestPki pki = TestPki.get();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected TrustListService trustListService;

    @DynamicPropertySource
    static void lotlMode(DynamicPropertyRegistry registry) {
        registry.add("signcheck.trust.mode", () -> "LOTL");
        registry.add("signcheck.trust.lotl-url", () -> TestTrustedLists.LOTL_URL);
        registry.add("signcheck.trust.official-journal-url", () -> TestTrustedLists.OFFICIAL_JOURNAL_URL);
        registry.add("signcheck.trust.lotl-keystore.path",
                () -> TestPkiFiles.trustStore("oj-lotl-signers", TestPki.get().lotlSigner().certificate()));
        registry.add("signcheck.trust.lotl-keystore.password", () -> TestPkiFiles.PASSWORD);
        registry.add("signcheck.trust.cache-dir", () -> TestPkiFiles.tempDirectory("signcheck-tl-cache"));
    }

    /** The initial load runs in the background after startup; wait until it has finished. */
    @BeforeEach
    void waitForTrustedLists() {
        await().atMost(Duration.ofSeconds(30)).until(() -> !trustListService.status().loading());
    }

    protected ResultActions validate(byte[] signed) throws Exception {
        return mockMvc.perform(multipart("/api/v1/validations")
                .file(new MockMultipartFile("file", "signed.pdf", MediaType.APPLICATION_PDF_VALUE, signed)));
    }
}
