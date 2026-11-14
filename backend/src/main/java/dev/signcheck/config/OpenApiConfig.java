package dev.signcheck.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.boot.info.BuildProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata of the OpenAPI document served at {@code /v3/api-docs} (Swagger UI: {@code /swagger-ui.html}).
 * Operation and field descriptions come from the controllers' annotations and from the Javadoc of the
 * DTOs, which springdoc reads at runtime (therapi-runtime-javadoc), so documentation lives in one place.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI signCheckOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        return new OpenAPI().info(new Info()
                .title("SignCheck API")
                .version(build == null ? "dev" : build.getVersion())
                .description("""
                        Validation of advanced electronic signatures (PAdES, XAdES, CAdES) following eIDAS and the \
                        ETSI standards, built on the EU DSS library. Uploaded files are processed in memory and \
                        never stored. Errors follow RFC 9457 (application/problem+json) with a stable `code`.""")
                .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
