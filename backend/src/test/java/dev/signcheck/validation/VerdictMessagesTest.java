package dev.signcheck.validation;

import static org.assertj.core.api.Assertions.assertThat;

import eu.europa.esig.dss.enumerations.SubIndication;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;

class VerdictMessagesTest {

    private static final Locale SPANISH = Locale.of("es");

    private final VerdictMessages messages = new VerdictMessages(messageSource());

    @ParameterizedTest
    @EnumSource(SubIndication.class)
    void shouldExplainEverySubIndicationInEnglishAndSpanish(SubIndication subIndication) {
        String english = messages.reason(subIndication, Locale.ENGLISH);
        String spanish = messages.reason(subIndication, SPANISH);

        assertThat(english).as("English text").isNotBlank().isNotEqualTo(subIndication.name());
        assertThat(spanish).as("Spanish text").isNotBlank().isNotEqualTo(subIndication.name()).isNotEqualTo(english);
    }

    @Test
    void shouldFallBackToEnglishForOtherLanguages() {
        assertThat(messages.reason(SubIndication.HASH_FAILURE, Locale.GERMAN))
                .isEqualTo("The document was modified after it was signed.");
    }

    @Test
    void shouldReturnNoReasonWithoutSubIndication() {
        assertThat(messages.reason(null, Locale.ENGLISH)).isNull();
    }

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false); // same as spring.messages.fallback-to-system-locale
        return source;
    }
}
