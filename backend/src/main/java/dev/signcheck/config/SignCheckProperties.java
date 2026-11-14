package dev.signcheck.config;

import dev.signcheck.trust.TrustMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
        @Valid @NotNull Signing signing,
        @Valid @NotNull Cors cors) {

    /**
     * Limits applied to uploads.
     *
     * @param maxFileSize    maximum size of each uploaded file
     * @param maxRequestSize maximum size of a whole request (signed file plus originals)
     */
    public record Upload(@NotNull DataSize maxFileSize, @NotNull DataSize maxRequestSize) {
    }

    /**
     * Trust anchors configuration.
     *
     * @param mode               where trust anchors come from
     * @param lotlUrl            URL of the EU List of Trusted Lists (used in {@link TrustMode#LOTL})
     * @param officialJournalUrl Official Journal publication announcing the certificates allowed to sign
     *                           the LOTL (used in {@link TrustMode#LOTL})
     * @param lotlKeystore       PKCS#12 with the LOTL signing certificates published in that Official
     *                           Journal; required in {@link TrustMode#LOTL}, never committed to the repository
     * @param cacheDir           local cache for downloaded Trusted Lists
     * @param refreshCron        Spring cron expression for the Trusted Lists refresh
     * @param keystore           trusted certificates keystore (used in {@link TrustMode#KEYSTORE}, and in
     *                           addition to the Trusted Lists in {@link TrustMode#LOTL} when it exists)
     */
    public record Trust(
            @NotNull TrustMode mode,
            @NotNull URI lotlUrl,
            @NotNull URI officialJournalUrl,
            @Valid Keystore lotlKeystore,
            @NotNull Path cacheDir,
            @NotBlank String refreshCron,
            @Valid @NotNull Keystore keystore) {
    }

    /**
     * PKCS#12 keystore holding certificates (no private keys are needed).
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
     * Cross-origin access to the API from browsers.
     *
     * @param allowedOrigins origins allowed to call {@code /api/**}; empty (the default) disables CORS, which is
     *                       the case in production where the frontend is served from the same origin by nginx
     */
    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    /**
     * Signature creation with the application's own demo certificate authority.
     *
     * @param enabled   whether the signing endpoint is active (the demo CA is then also a trust anchor)
     * @param tsaUrl    RFC 3161 timestamping authority URL for level T signatures; empty disables level T
     * @param demoCaDir directory where the demo CA keystore is generated on first start and reused afterwards;
     *                  never inside the repository
     */
    public record Signing(boolean enabled, String tsaUrl, Path demoCaDir) {

        public boolean timestampingEnabled() {
            return tsaUrl != null && !tsaUrl.isBlank();
        }
    }
}
