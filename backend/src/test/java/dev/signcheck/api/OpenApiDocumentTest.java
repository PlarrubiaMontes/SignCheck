package dev.signcheck.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The published OpenAPI document describes every endpoint, its errors and its fields. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentTest {

    private static final String VALIDATIONS = "$.paths['/api/v1/validations'].post";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldDescribeTheApi() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.info.title").value("SignCheck API"))
                .andExpect(jsonPath("$.info.license.name").value("Apache-2.0"));
    }

    @Test
    void shouldDocumentTheValidationEndpointAndItsErrors() throws Exception {
        apiDocs()
                .andExpect(jsonPath(VALIDATIONS + ".summary").isNotEmpty())
                .andExpect(jsonPath(VALIDATIONS + ".requestBody.content['multipart/form-data'].schema.properties",
                        hasKey("file")))
                .andExpect(jsonPath(VALIDATIONS + ".responses", hasKey("200")))
                .andExpect(jsonPath(VALIDATIONS + ".responses", hasKey("400")))
                .andExpect(jsonPath(VALIDATIONS + ".responses", hasKey("413")))
                .andExpect(jsonPath(VALIDATIONS + ".responses", hasKey("422")))
                .andExpect(jsonPath(VALIDATIONS + ".responses", hasKey("500")))
                .andExpect(jsonPath(VALIDATIONS + ".responses['422'].content['application/problem+json'].examples",
                        hasKey("UNSUPPORTED_FORMAT")));
    }

    @Test
    void shouldDocumentTheTrustStatusEndpoint() throws Exception {
        apiDocs().andExpect(jsonPath("$.paths['/api/v1/trust/status'].get.summary").isNotEmpty());
    }

    @Test
    void shouldReuseTheJavadocOfTheDtosAsFieldDescriptions() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.components.schemas.SignatureResult.properties.subIndication.description")
                        .value(containsString("EN 319 102-1")))
                .andExpect(jsonPath("$.components.schemas.TrustStatus.properties.loading.description").isNotEmpty());
    }

    private ResultActions apiDocs() throws Exception {
        return mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
