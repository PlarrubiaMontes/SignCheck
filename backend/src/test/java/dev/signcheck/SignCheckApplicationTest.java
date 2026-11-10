package dev.signcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signcheck.config.SignCheckProperties;
import dev.signcheck.trust.TrustMode;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.unit.DataSize;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignCheckApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignCheckProperties properties;

    @Test
    void shouldLoadApplicationContextWithDefaultPropertiesFromApplicationYml() {
        assertThat(properties.upload().maxFileSize()).isEqualTo(DataSize.ofMegabytes(20));
        assertThat(properties.trust().mode()).isEqualTo(TrustMode.NONE);
        assertThat(properties.trust().lotlUrl())
                .isEqualTo(URI.create("https://ec.europa.eu/tools/lotl/eu-lotl.xml"));
        assertThat(properties.trust().cacheDir().getFileName()).hasToString("signcheck-tl-cache");
        assertThat(properties.trust().refreshCron()).isEqualTo("0 0 */6 * * *");
        assertThat(properties.trust().keystore().path().getFilename()).isEqualTo("demo-ca.p12");
        assertThat(properties.network().connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.network().readTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.signing().enabled()).isFalse();
    }

    @Test
    void shouldReportHealthUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldServeOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").exists());
    }

    @Test
    void shouldServeSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
