package dev.signcheck.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.signcheck.trust.TrustMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Binding and validation rules of {@link SignCheckProperties}, tested on a minimal context
 * (no web server, no application.yml) so each case states exactly the properties it uses.
 */
class SignCheckPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues(
                    "signcheck.upload.max-file-size=5MB",
                    "signcheck.trust.mode=LOTL",
                    "signcheck.trust.lotl-url=https://example.test/lotl.xml",
                    "signcheck.trust.cache-dir=/tmp/tl",
                    "signcheck.trust.refresh-cron=0 0 * * * *",
                    "signcheck.trust.keystore.password=secret-test-only",
                    "signcheck.network.connect-timeout=1s",
                    "signcheck.network.read-timeout=2s",
                    "signcheck.signing.enabled=false");

    @Test
    void shouldStartWithACompleteConfiguration() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldBindTrustModeCaseInsensitively() {
        runner.withPropertyValues("signcheck.trust.mode=keystore")
                .run(context -> assertThat(context.getBean(SignCheckProperties.class).trust().mode())
                        .isEqualTo(TrustMode.KEYSTORE));
    }

    @Test
    void shouldFailToStartWhenTrustModeIsUnknown() {
        runner.withPropertyValues("signcheck.trust.mode=EVERYTHING")
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause().hasMessageContaining("EVERYTHING"));
    }

    @Test
    void shouldFailToStartWhenRequiredValueIsBlank() {
        runner.withPropertyValues("signcheck.trust.refresh-cron=")
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause().hasMessageContaining("trust.refreshCron"));
    }

    @Test
    void shouldNotExposeKeystorePasswordInToString() {
        runner.run(context -> assertThat(context.getBean(SignCheckProperties.class).toString())
                .doesNotContain("secret-test-only")
                .contains("password=****"));
    }

    @EnableConfigurationProperties(SignCheckProperties.class)
    static class PropertiesConfig {
    }
}
