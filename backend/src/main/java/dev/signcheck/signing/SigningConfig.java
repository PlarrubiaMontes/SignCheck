package dev.signcheck.signing;

import dev.signcheck.config.SignCheckProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Demo CA, only when signing is enabled ({@code signcheck.signing.enabled=true}). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "signcheck.signing", name = "enabled", havingValue = "true")
public class SigningConfig {

    @Bean
    DemoCertificateAuthority demoCertificateAuthority(SignCheckProperties properties) {
        return DemoCertificateAuthority.loadOrCreate(properties.signing().demoCaDir());
    }
}
