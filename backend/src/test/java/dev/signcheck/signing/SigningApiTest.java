package dev.signcheck.signing;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signcheck.testpki.OfflineNetworkConfig;
import dev.signcheck.testpki.SampleDocuments;
import dev.signcheck.testpki.SignedSamples;
import dev.signcheck.testpki.TestPki;
import dev.signcheck.testpki.TestPkiFiles;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sign → validate round trips (roadmap 7.4) in KEYSTORE mode: the demo CA is trusted because signing is
 * enabled, and level T uses the test PKI's TSA, trusted through the keystore.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({OfflineNetworkConfig.class, SigningApiTest.TestTsa.class})
class SigningApiTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void signingEnabled(DynamicPropertyRegistry registry) {
        registry.add("signcheck.trust.mode", () -> "KEYSTORE");
        registry.add("signcheck.trust.keystore.path",
                () -> TestPkiFiles.trustStore("test-root", TestPki.get().root().certificate()));
        registry.add("signcheck.trust.keystore.password", () -> TestPkiFiles.PASSWORD);
        registry.add("signcheck.signing.enabled", () -> "true");
        registry.add("signcheck.signing.demo-ca-dir", () -> TestPkiFiles.tempDirectory("signcheck-demo-ca"));
    }

    @ParameterizedTest(name = "{0} {1} of {2}")
    @CsvSource({
            "PADES, ENVELOPED, pdf",
            "XADES, ENVELOPED, xml",
            "XADES, ENVELOPING, xml",
            "CADES, ENVELOPING, txt"})
    void shouldSignDocumentsThatValidateAsValid(String format, String packaging, String type) throws Exception {
        byte[] signed = sign(sample(type), "sample." + type, format, packaging, "B");

        validate(signed, "signed")
                .andExpect(jsonPath("$.summary.overallVerdict").value("VALID"))
                .andExpect(jsonPath("$.signatures[0].signer.commonName").value("SignCheck DEMO Signer - TEST ONLY"))
                .andExpect(jsonPath("$.signatures[0].level").value(containsString("BASELINE-B")));
    }

    @ParameterizedTest(name = "{0} detached")
    @CsvSource({"XADES", "CADES"})
    void shouldProduceDetachedSignaturesThatValidateWithTheOriginal(String format) throws Exception {
        byte[] signature = sign(SampleDocuments.text(), "original.txt", format, "DETACHED", "B");

        validate(signature, "signature", new MockMultipartFile("originals", "original.txt",
                MediaType.TEXT_PLAIN_VALUE, SampleDocuments.text()))
                .andExpect(jsonPath("$.signatures[0].verdict").value("VALID"));
    }

    @Test
    void shouldAddATrustedTimestampAtLevelT() throws Exception {
        byte[] signed = sign(SampleDocuments.pdf(), "contract.pdf", "PADES", null, "T");

        validate(signed, "contract-signed.pdf")
                .andExpect(jsonPath("$.signatures[0].verdict").value("VALID"))
                .andExpect(jsonPath("$.signatures[0].level").value("PAdES-BASELINE-T"))
                .andExpect(jsonPath("$.signatures[0].timestamps", hasSize(1)))
                .andExpect(jsonPath("$.signatures[0].timestamps[0].valid").value(true));
    }

    @Test
    void shouldNameAndTypeTheDownload() throws Exception {
        mockMvc.perform(multipart("/api/v1/signatures").file(file("contract.pdf", SampleDocuments.pdf()))
                        .param("format", "PADES"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("contract-signed.pdf")));
    }

    @Test
    void shouldRejectUnsupportedCombinations() throws Exception {
        mockMvc.perform(multipart("/api/v1/signatures").file(file("a.txt", SampleDocuments.text()))
                        .param("format", "CADES").param("packaging", "ENVELOPED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNING_REQUEST"));
    }

    @Test
    void shouldRejectAFileThatDoesNotFitTheFormat() throws Exception {
        mockMvc.perform(multipart("/api/v1/signatures").file(file("a.txt", SampleDocuments.text()))
                        .param("format", "PADES"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNING_REQUEST"));
    }

    @Test
    void shouldAdvertiseLevelTWhenATimestampingAuthorityIsAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/signatures/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formats", hasSize(3)))
                .andExpect(jsonPath("$.levels[1]").value("T"))
                .andExpect(jsonPath("$.signer").value(containsString("TEST ONLY")));
    }

    private byte[] sign(byte[] content, String name, String format, String packaging, String level) throws Exception {
        var request = multipart("/api/v1/signatures").file(file(name, content))
                .param("format", format).param("level", level);
        if (packaging != null) {
            request.param("packaging", packaging);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return result.getResponse().getContentAsByteArray();
    }

    private ResultActions validate(byte[] signed, String name, MockMultipartFile... originals) throws Exception {
        var request = multipart("/api/v1/validations").file(file(name, signed));
        for (MockMultipartFile original : originals) {
            request.file(original);
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, MediaType.APPLICATION_OCTET_STREAM_VALUE, content);
    }

    private static byte[] sample(String type) {
        return switch (type) {
            case "pdf" -> SampleDocuments.pdf();
            case "xml" -> SampleDocuments.xml();
            default -> SampleDocuments.text();
        };
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestTsa {

        @Bean
        TSPSource testTspSource() {
            return SignedSamples.testTsa();
        }
    }
}
