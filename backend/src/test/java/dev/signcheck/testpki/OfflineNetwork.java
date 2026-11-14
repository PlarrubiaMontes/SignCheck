package dev.signcheck.testpki;

import dev.signcheck.config.NetworkDataLoaders;
import eu.europa.esig.dss.spi.client.http.MemoryDataLoader;
import java.security.cert.CRLException;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory replacement for {@link NetworkDataLoaders}: answers the URLs of the test PKI (its CRLs) and
 * any extra URL a test registers (e.g. Trusted Lists) without touching the network. Unknown URLs fail like
 * an unreachable server would.
 */
public final class OfflineNetwork {

    private OfflineNetwork() {
    }

    /** Loaders serving the test PKI CRLs plus {@code extra} URL → content pairs. */
    public static NetworkDataLoaders loaders(Map<String, byte[]> extra) {
        TestPki pki = TestPki.get();
        Map<String, byte[]> content = new HashMap<>(extra);
        try {
            content.put(TestPki.CRL_URL, pki.crl().getEncoded());
            content.put(TestPki.ROOT_CRL_URL, pki.rootCrl().getEncoded());
        } catch (CRLException e) {
            throw new IllegalStateException(e);
        }
        MemoryDataLoader loader = new MemoryDataLoader(content);
        return new NetworkDataLoaders(loader, loader);
    }
}
