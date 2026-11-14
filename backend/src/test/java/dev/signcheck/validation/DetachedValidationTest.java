package dev.signcheck.validation;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signcheck.testpki.SampleDocuments;
import dev.signcheck.testpki.SignedSamples;
import dev.signcheck.testpki.TestPki;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Detached signatures (XAdES and CAdES) with the {@code originals} part: the verdict depends on whether
 * the original document is provided and whether it is the one that was signed.
 */
class DetachedValidationTest extends ValidationApiTestSupport {

    private static final String ORIGINAL = SignedSamples.DETACHED_ORIGINAL_NAME;

    static Stream<Arguments> detachedSignatures() {
        return Stream.of(
                Arguments.of("XAdES", "signature.xml",
                        (Supplier<byte[]>) () -> SignedSamples.xadesDetached(TestPki.get().valid())),
                Arguments.of("CAdES", "signature.p7s",
                        (Supplier<byte[]>) () -> SignedSamples.cadesDetached(TestPki.get().valid())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("detachedSignatures")
    void shouldReportIndeterminateWhenOriginalIsMissing(String format, String fileName,
            Supplier<byte[]> signature) throws Exception {
        validate(fileName, signature.get())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectedFormat").value(format))
                .andExpect(jsonPath("$.signatures[0].verdict").value("INDETERMINATE"))
                .andExpect(jsonPath("$.signatures[0].subIndication").value("SIGNED_DATA_NOT_FOUND"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("detachedSignatures")
    void shouldReportInvalidWhenOriginalIsNotTheSignedDocument(String format, String fileName,
            Supplier<byte[]> signature) throws Exception {
        byte[] modifiedOriginal = SignedSamples.tamper(SampleDocuments.text());

        validate(fileName, signature.get(), original(ORIGINAL, modifiedOriginal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatures[0].verdict").value("INVALID"))
                .andExpect(jsonPath("$.signatures[0].subIndication").value("HASH_FAILURE"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("detachedSignatures")
    void shouldReportValidWhenTheSignedOriginalIsProvided(String format, String fileName,
            Supplier<byte[]> signature) throws Exception {
        validate(fileName, signature.get(), original(ORIGINAL, SampleDocuments.text()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatures[0].verdict").value("VALID"))
                .andExpect(jsonPath("$.summary.overallVerdict").value("VALID"));
    }
}
