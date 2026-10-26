package com.drivique.api.reservations;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.service.CaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Crear una reserva exige un captcha validado por el servidor. */
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class CaptchaInterceptorIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired private MockMvc mvc;
    @MockitoBean private CaptchaService captcha;

    @BeforeEach
    void captchaRejectsEverythingExceptAGoodToken() {
        when(captcha.isEnabled()).thenReturn(true);
        when(captcha.getSiteKey()).thenReturn("test-site-key");
        doThrow(new IllegalArgumentException("Completa la verificación de seguridad (captcha) para continuar."))
                .when(captcha).verify(isNull(), any());
        doThrow(new IllegalArgumentException("La verificación de seguridad no fue superada o expiró. Vuelve a intentarlo."))
                .when(captcha).verify(eq("bad-token"), any());
    }

    @Test
    @WithMockUser(username = "client@drivique.com", roles = "CUSTOMER")
    void creatingAReservationWithoutCaptchaTokenIsRefused() throws Exception {
        mvc.perform(post("/api/v1/reservations/checkout/initiate").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("captcha")));
    }

    @Test
    @WithMockUser(username = "client@drivique.com", roles = "CUSTOMER")
    void creatingAReservationWithARejectedCaptchaTokenIsRefused() throws Exception {
        mvc.perform(post("/api/v1/reservations/checkout/initiate").contextPath("/api")
                        .header("X-Captcha-Token", "bad-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no fue superada")));
    }

    @Test
    @WithMockUser(username = "client@drivique.com", roles = "CUSTOMER")
    void aValidCaptchaTokenLetsTheRequestReachTheReservationValidation() throws Exception {
        // Pasa el captcha y recién entonces se valida el cuerpo (vacío => datos inválidos)
        mvc.perform(post("/api/v1/reservations/checkout/initiate").contextPath("/api")
                        .header("X-Captcha-Token", "good-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("captcha"))));
    }

    @Test
    void captchaConfigIsPublicAndNeverExposesTheSecret() throws Exception {
        mvc.perform(post("/api/v1/captcha/config").contextPath("/api"))
                .andExpect(status().is4xxClientError());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/captcha/config").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("secret"))));
    }
}
