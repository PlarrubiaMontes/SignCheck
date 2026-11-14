package dev.signcheck.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Browser access rules for the API. CORS is only opened for the configured origins (the Angular dev server in
 * the {@code dev} profile); in production the UI and the API share the same origin, so no CORS is needed.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    private final SignCheckProperties properties;

    public WebConfig(SignCheckProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var origins = properties.cors().allowedOrigins();
        if (origins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods(HttpMethod.GET.name(), HttpMethod.POST.name())
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
