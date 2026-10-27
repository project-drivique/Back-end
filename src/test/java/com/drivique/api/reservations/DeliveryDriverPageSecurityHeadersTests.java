package com.drivique.api.reservations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.drivique.api.DatabaseHealthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class DeliveryDriverPageSecurityHeadersTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Test
    void theDriverPageKeepsItsOwnStylesInsteadOfTheStrictApiPolicy() throws Exception {
        var result = mvc.perform(get("/api/v1/public/deliveries/action").contextPath("/api")
                        .param("token", "invalid").accept(MediaType.TEXT_HTML))
                .andReturn().getResponse();

        String policy = result.getHeader("Content-Security-Policy");

        assertThat(result.getContentType()).contains("text/html");
        // Con la política general (solo recursos propios) el navegador descartaba el diseño y se veía texto plano
        assertThat(policy).contains("style-src").contains("'unsafe-inline'").contains("fonts.googleapis.com");
        assertThat(policy).contains("frame-ancestors 'none'");
    }
}
