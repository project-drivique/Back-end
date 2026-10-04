package com.drivique.api.catalog;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.dto.BrandRequestDTO;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class BrandIntegrationTests extends DatabaseHealthTestSupport {
    @Autowired MockMvc mvc;
    private static final String BODY = """
        {"companyName":"Drivique","logoUrl":"https://example.com/logo.png","faviconUrl":null,
         "primaryColor":"#abcdef","secondaryColor":"#123456","accentColor":"#654321","defaultTheme":"DARK"}
        """;
    @BeforeEach
    void setupBrand() {
        jdbc.update("delete from core.brand_configurations");
        jdbc.update("delete from iam.security_configurations");
        jdbc.update("insert into core.brand_configurations (id,company_name,primary_color,secondary_color,accent_color,default_theme,is_active) values (?, 'Original','#2563EB','#1E3A8A','#60A5FA','SYSTEM',true)", UUID.randomUUID());
    }
    @Test
    void publicBrandReturnsOnlyActiveConfiguration() throws Exception {
        jdbc.update("insert into core.brand_configurations (id,company_name,primary_color,secondary_color,accent_color,default_theme,is_active) values (?, 'Inactive','#2563EB','#1E3A8A','#60A5FA','LIGHT',false)", UUID.randomUUID());
        mvc.perform(get("/api/v1/brand-configurations/active").contextPath("/api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyName").value("Original"))
                .andExpect(jsonPath("$.defaultTheme").value("SYSTEM"))
                .andExpect(jsonPath("$.primaryColor").value("#2563EB"))
                .andExpect(jsonPath("$.createdAt").doesNotExist());
    }
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void missingActiveBrandReturns404ForReadAndWrite() throws Exception {
        jdbc.update("update core.brand_configurations set is_active = false");
        mvc.perform(get("/api/v1/brand-configurations/active").contextPath("/api"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/brand-configurations").contextPath("/api").contentType("application/json").content(BODY))
                .andExpect(status().isNotFound());
    }
    @Test
    void anonymousCannotUpdate() throws Exception {
        mvc.perform(put("/api/v1/brand-configurations").contextPath("/api").contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
    }
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotUpdate() throws Exception {
        mvc.perform(put("/api/v1/brand-configurations").contextPath("/api").contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());
    }
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void adminPersistsBrandAndPublicReadReflectsChanges() throws Exception {
        mvc.perform(put("/api/v1/brand-configurations").contextPath("/api").contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.primaryColor").value("#ABCDEF"));
        mvc.perform(get("/api/v1/brand-configurations/active").contextPath("/api"))
                .andExpect(jsonPath("$.companyName").value("Drivique"))
                .andExpect(jsonPath("$.logoUrl").value("https://example.com/logo.png"))
                .andExpect(jsonPath("$.defaultTheme").value("DARK"));
        assertThat(jdbc.queryForObject("select count(*) from core.brand_configurations", Integer.class)).isEqualTo(1);
    }
    @ParameterizedTest
    @ValueSource(strings = {"primaryColor", "secondaryColor", "accentColor"})
    @WithMockUser(roles = "SUPER_ADMIN")
    void validatesEveryHexField(String field) throws Exception {
        String invalid = BODY.replaceAll("\"" + field + "\":\"#[a-zA-Z0-9]+\"", "\"" + field + "\":\"#xyz\"");
        mvc.perform(put("/api/v1/brand-configurations").contextPath("/api").contentType("application/json").content(invalid))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        assertThat(jdbc.queryForObject("select company_name from core.brand_configurations", String.class)).isEqualTo("Original");
    }
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void rejectsDuplicateColorsInvalidThemeAndUnsafeUrl() throws Exception {
        for (String body : new String[]{BODY.replace("#123456", "#ABCDEF"), BODY.replace("DARK", "AUTO"),
                BODY.replace("https://example.com/logo.png", "javascript:alert(1)"), BODY.replace("Drivique", " ")}) {
            mvc.perform(put("/api/v1/brand-configurations").contextPath("/api").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }
    @Test
    void publicSecurityUsesAllowlistAndOmitsDescriptionsAndInternalKeys() throws Exception {
        for (String key : new String[]{"SESSION_IDLE_TIMEOUT_MINUTES", "PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES",
                "MAX_LOGIN_ATTEMPTS", "MFA_REQUIRED_FOR_ADMINS", "JWT_SECRET", "FUTURE_SETTING"}) {
            jdbc.update("insert into iam.security_configurations (id,config_key,config_value) values (?,?,?)", UUID.randomUUID(), key, "30");
        }
        mvc.perform(get("/api/v1/security-configurations").contextPath("/api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].configKey").value("PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES"))
                .andExpect(jsonPath("$[1].configKey").value("SESSION_IDLE_TIMEOUT_MINUTES"))
                .andExpect(jsonPath("$[0].description").doesNotExist());
        jdbc.update("delete from iam.security_configurations");
        mvc.perform(get("/api/v1/security-configurations").contextPath("/api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    @Test
    void swaggerIncludesNewContracts() throws Exception {
        mvc.perform(get("/api/v3/api-docs").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/v1/brand-configurations'].put").exists())
                .andExpect(jsonPath("$.paths['/v1/security-configurations'].get").exists())
                .andExpect(jsonPath("$.components.schemas.BrandRequestDTO.properties.primaryColor.pattern").value("^#[0-9A-Fa-f]{6}$"));
    }
}
