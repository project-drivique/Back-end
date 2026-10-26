package com.drivique.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class KycFramingHeaderFilterTests {

    private final KycFramingHeaderFilter filter = new KycFramingHeaderFilter(
            "https://drivique.tech/", "https://app.drivique.tech,http://localhost:5173");

    private MockHttpServletResponse call(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void kycFileByPathCanBeFramedByOurFrontends() throws Exception {
        MockHttpServletResponse response = call("GET", "/api/v1/kyc/documents/file");

        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("frame-ancestors 'self' https://drivique.tech https://app.drivique.tech http://localhost:5173")
                .doesNotContain("'none'");
    }

    @Test
    void kycFileById_IsAlsoCovered() throws Exception {
        assertThat(call("GET", "/api/v1/kyc/documents/3f2c1a9e-aaaa-bbbb-cccc-123456789012/file").getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
    }

    @Test
    void everythingElseKeepsTheStrictDefaults() throws Exception {
        assertThat(call("GET", "/api/v1/reservations/me").getHeader("X-Frame-Options")).isNull();
        assertThat(call("GET", "/api/v1/kyc/documents").getHeader("X-Frame-Options")).isNull();
        assertThat(call("POST", "/api/v1/kyc/documents/file").getHeader("X-Frame-Options")).isNull();
    }
}
