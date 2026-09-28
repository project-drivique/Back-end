package com.drivique.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class OpenApiIntegrationTests extends DatabaseHealthTestSupport {
    @Autowired MockMvc mvc;

    @Test
    void openApiContractIsPublicAndDocumentsErrorSchema() throws Exception {
        mvc.perform(get("/api/v3/api-docs").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", startsWith("3.0.")))
                .andExpect(jsonPath("$.info.title").value("Drivique API"))
                .andExpect(jsonPath("$.info.version").value("0.0.1-SNAPSHOT"))
                .andExpect(jsonPath("$.components.schemas.ApiProblem.properties.errors").exists())
                .andExpect(jsonPath("$.components.responses.Error400").exists())
                .andExpect(jsonPath("$.components.responses.Error500").exists())
                .andExpect(content().string(not(containsString("/test-errors"))));
    }

    @Test
    void swaggerRedirectAssetsAndConfigurationArePublic() throws Exception {
        mvc.perform(get("/api/swagger-ui.html").contextPath("/api"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/swagger-ui/index.html")));
        mvc.perform(get("/api/swagger-ui/index.html").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("swagger-ui")));
        mvc.perform(get("/api/v3/api-docs/swagger-config").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/api/v3/api-docs"));
    }
}
