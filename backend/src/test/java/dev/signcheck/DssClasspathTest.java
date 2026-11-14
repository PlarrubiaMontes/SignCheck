package dev.signcheck;

import static org.assertj.core.api.Assertions.assertThat;

import eu.europa.esig.dss.cms.ICMSUtils;
import eu.europa.esig.dss.crl.ICRLUtils;
import eu.europa.esig.dss.model.policy.ValidationPolicy;
import eu.europa.esig.dss.pdf.IPdfObjFactory;
import eu.europa.esig.dss.utils.IUtils;
import eu.europa.esig.dss.validation.DocumentValidatorFactory;
import eu.europa.esig.dss.validation.policy.ValidationPolicyLoader;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/**
 * Guards the DSS module selection in {@code pom.xml}.
 *
 * <p>DSS splits several features into an API module and interchangeable implementations that are
 * discovered at runtime with {@link ServiceLoader} (utils, CMS, CRL parsing, PDF backend). A missing
 * or duplicated implementation compiles fine and only fails when the first document is validated,
 * so this test makes the wiring explicit and fails fast.
 */
class DssClasspathTest {

    @Test
    void shouldHaveExactlyOneImplementationOfEachExclusiveDssService() {
        assertThat(implementationsOf(IUtils.class))
                .containsExactly("eu.europa.esig.dss.utils.apache.impl.ApacheCommonsUtils");
        assertThat(implementationsOf(ICMSUtils.class))
                .containsExactly("eu.europa.esig.dss.cms.object.CMSObjectUtils");
        assertThat(implementationsOf(ICRLUtils.class))
                .containsExactly("eu.europa.esig.dss.crl.stream.impl.CRLUtilsStreamImpl");
    }

    @Test
    void shouldUsePdfBoxAsPdfBackend() {
        assertThat(implementationsOf(IPdfObjFactory.class))
                .isNotEmpty()
                .allMatch(name -> name.startsWith("eu.europa.esig.dss.pdf.pdfbox."));
    }

    @Test
    void shouldRegisterAValidatorForEachSupportedFormat() {
        assertThat(implementationsOf(DocumentValidatorFactory.class)).contains(
                "eu.europa.esig.dss.pades.validation.PDFDocumentValidatorFactory",
                "eu.europa.esig.dss.xades.validation.XMLDocumentValidatorFactory",
                "eu.europa.esig.dss.cades.validation.CMSDocumentValidatorFactory");
    }

    @Test
    void shouldLoadTheDefaultEtsiValidationPolicy() throws Exception {
        // Exercises dss-policy-jaxb and the JAXB runtime managed by Spring Boot (4.x instead of DSS's 3.x)
        ValidationPolicy policy = ValidationPolicyLoader.fromDefaultValidationPolicy().create();

        assertThat(policy.getPolicyName()).isNotBlank();
    }

    private static List<String> implementationsOf(Class<?> service) {
        return ServiceLoader.load(service).stream()
                .map(provider -> provider.type().getName())
                .toList();
    }
}
