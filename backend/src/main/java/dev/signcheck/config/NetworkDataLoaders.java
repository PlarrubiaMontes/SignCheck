package dev.signcheck.config;

import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.http.commons.OCSPDataLoader;
import eu.europa.esig.dss.spi.client.http.DataLoader;
import java.time.Duration;
import java.util.Objects;

/**
 * The only way SignCheck reaches the network: every download of Trusted Lists, CRLs, intermediate
 * certificates (AIA) and every OCSP request goes through these DSS data loaders.
 *
 * <p>Keeping them in one bean gives tests a single seam: they replace it with in-memory loaders that
 * serve generated CRLs and Trusted Lists, so no test ever needs internet (CLAUDE.md, rule 6).
 *
 * @param http GET loader (Trusted Lists, CRLs, AIA)
 * @param ocsp POST loader with the OCSP content type
 */
public record NetworkDataLoaders(DataLoader http, DataLoader ocsp) {

    public NetworkDataLoaders {
        Objects.requireNonNull(http, "http");
        Objects.requireNonNull(ocsp, "ocsp");
    }

    /** Real HTTP loaders (Apache HttpClient) with the configured timeouts. */
    public static NetworkDataLoaders online(SignCheckProperties.Network network) {
        return new NetworkDataLoaders(
                withTimeouts(new CommonsDataLoader(), network),
                withTimeouts(new OCSPDataLoader(), network));
    }

    private static CommonsDataLoader withTimeouts(CommonsDataLoader loader, SignCheckProperties.Network network) {
        loader.setTimeoutConnection(millis(network.connectTimeout()));
        loader.setTimeoutConnectionRequest(millis(network.connectTimeout()));
        loader.setTimeoutResponse(millis(network.readTimeout()));
        loader.setTimeoutSocket(millis(network.readTimeout()));
        return loader;
    }

    private static int millis(Duration duration) {
        return Math.toIntExact(duration.toMillis());
    }
}
