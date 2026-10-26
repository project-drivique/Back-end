package com.drivique.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CaptchaServiceTests {

    private static final String URL = "https://challenges.test/siteverify";
    private MockRestServiceServer server;
    private CaptchaService captcha;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        captcha = new CaptchaService(true, "site", "secret-123", URL, true, builder.build());
    }

    @Test
    void validTokenPasses_andTheSecretNeverLeavesTheServer() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("secret=secret-123")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("response=good-token")))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> captcha.verify("good-token", "1.2.3.4")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void rejectedTokenIsRefused() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> captcha.verify("used-token", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no fue superada");
    }

    @Test
    void missingTokenIsRefusedWithoutCallingTheProvider() {
        assertThatThrownBy(() -> captcha.verify(" ", null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("captcha");
        assertThatThrownBy(() -> captcha.verify(null, null)).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void providerOutageNeverLetsTheRequestThrough() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> captcha.verify("any", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disabledCaptchaSkipsVerification() {
        CaptchaService off = new CaptchaService(false, "", "", URL, true, RestClient.create());
        assertThat(off.isEnabled()).isFalse();
        assertThatCode(() -> off.verify(null, null)).doesNotThrowAnyException();
    }

    @Test
    void protectedEnvironmentRefusesToStartWithoutKeys() {
        assertThatThrownBy(() -> new CaptchaService(true, "", "", URL, true, RestClient.create()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TURNSTILE_SECRET_KEY");
    }

    @Test
    void developmentFallsBackToCloudflareTestKeys() {
        CaptchaService dev = new CaptchaService(true, "", "", URL, false, RestClient.create());
        assertThat(dev.getSiteKey()).isEqualTo(CaptchaService.TEST_SITE_KEY);
    }
}
