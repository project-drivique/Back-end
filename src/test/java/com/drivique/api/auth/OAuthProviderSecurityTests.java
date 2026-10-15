package com.drivique.api.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.drivique.api.dto.SocialLoginRequestDTO;
import com.drivique.api.service.OAuthProviderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

/** El servicio real nunca acepta tokens de prueba ni correos que mande el cliente. */
class OAuthProviderSecurityTests {

    private final OAuthProviderService service = new OAuthProviderService(new ObjectMapper());

    private static SocialLoginRequestDTO request(String provider, String idToken, String accessToken, String email) {
        return new SocialLoginRequestDTO(provider, idToken, accessToken, null, null, null, null, null, email, "Ana", "Gomez");
    }

    @Test
    void sandboxStyleTokensAreRejected() {
        assertThatThrownBy(() -> service.verifyAndExtract(request("GOOGLE", "sandbox_google_token:admin@drivique.com", null, null)))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.verifyAndExtract(request("FACEBOOK", null, "sandbox_fb_token:admin@drivique.com", null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void anUnsignedJwtIsNotTrustedAndTheClientEmailNeverLogsAnyoneIn() {
        String forged = "eyJhbGciOiJub25lIn0.eyJpc3MiOiJhY2NvdW50cy5nb29nbGUuY29tIiwiZW1haWwiOiJhZG1pbkBkcml2aXF1ZS5jb20iLCJzdWIiOiIxIn0.x";
        assertThatThrownBy(() -> service.verifyAndExtract(request("GOOGLE", forged, null, "admin@drivique.com")))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.verifyAndExtract(request("FACEBOOK", null, "token-invalido", "admin@drivique.com")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
