package dev.signcheck.testpki;

import dev.signcheck.config.NetworkDataLoaders;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Import into a Spring test to replace the application's HTTP data loaders with {@link OfflineNetwork}:
 * the real trust and revocation configuration runs, but every download is answered from memory.
 */
@TestConfiguration(proxyBeanMethods = false)
public class OfflineNetworkConfig {

    @Bean
    @Primary
    NetworkDataLoaders offlineNetworkDataLoaders() {
        return OfflineNetwork.loaders(Map.of());
    }
}
