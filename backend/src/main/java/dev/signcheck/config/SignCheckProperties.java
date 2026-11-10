package dev.signcheck.config;

import dev.signcheck.trust.TrustMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Typed view of every {@code signcheck.*} property (see {@code docs/ARCHITECTURE.md} §3).
 *
 * <p>Defaults live in {@code application.yml}; profiles override them. The values are bound and
 * validated at startup, so a misconfigured application fails fast instead of at the first request.
 */
@Validated
@ConfigurationProperties("signcheck")
public record SignCheckProperties(
        @Valid @NotNull Upload upload,
        @Valid @NotNull Trust trust,
        @Valid @NotNull Network network,
        @Valid @NotNull Signing signing) {

    /** Limits applied to uploaded files. */
    public record Upload(@NotNull DataSize maxFileSize) {
    }

    /**
     * Trust anchors configuration.
     *
     * @param mode        where trust anchors come from
     * @param lotlUrl     URL of the EU List of Trusted Lists (used in {@link TrustMode#LOTL})
     * @param cacheDir    local cache for downloaded Trusted Lists
     * @param refreshCron Spring cron expression for the Trusted Lists refresh
     * @param keystore    trusted certificates keystore (used in {@link TrustMode#KEYSTORE})
     */
    public record Trust(
            @NotNull TrustMode mode,
            @NotNull URI lotlUrl,
            @NotNull Path cacheDir,
            @NotBlank String refreshCron,
            @Valid @NotNull Keystore keystore) {
    }

    /**
     * PKCS#12 keystore with trusted certificates. The default is generated, test-only material.
     *
     * @param path     location of the keystore (e.g. {@code classpath:trust/demo-ca.p12})
     * @param password keystore password; the default is a TEST-ONLY value
     */
    public record Keystore(Resource path, String password) {

        /** Never expose the password through logs or actuator output. */
        @Override
        public String toString() {
            return "Keystore[path=" + path + ", password=****]";
        }
    }

    /** Timeouts for outgoing calls (Trusted Lists, OCSP, CRL, TSA). */
    public record Network(@NotNull Duration connectTimeout, @NotNull Duration readTimeout) {
    }

    /**
     * Signature creation (phase 2).
     *
     * @param enabled whether the signing endpoint is active
     * @param tsaUrl  timestamping authority URL for level T signatures; empty when not configured
     */
    public record Signing(boolean enabled, String tsaUrl) {
    }
}
