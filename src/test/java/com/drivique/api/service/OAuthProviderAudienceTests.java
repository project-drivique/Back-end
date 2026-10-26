package com.drivique.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.drivique.api.dto.OAuthUserInfo;
import com.drivique.api.dto.SocialLoginRequestDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

/** Un token solo sirve si fue emitido para esta aplicación y el correo siempre sale del proveedor. */
class OAuthProviderAudienceTests {

    private static final String OUR_GOOGLE_CLIENT = "123-web.apps.googleusercontent.com";
    private final ObjectMapper mapper = new ObjectMapper();
    private OAuthProviderService service;

    @BeforeEach
    void setUp() {
        service = spy(new OAuthProviderService(mapper));
        ReflectionTestUtils.setField(service, "googleClientId", OUR_GOOGLE_CLIENT + ", 456-android.apps.googleusercontent.com");
        ReflectionTestUtils.setField(service, "facebookAppId", "999");
        ReflectionTestUtils.setField(service, "facebookAppSecret", "fb-secret");
        ReflectionTestUtils.setField(service, "requireSecrets", true);
    }

    private JsonNode json(String raw) throws Exception {
        return mapper.readTree(raw);
    }

    private static SocialLoginRequestDTO google(String accessToken, String emailFromClient) {
        return new SocialLoginRequestDTO("GOOGLE", null, accessToken, null, null, null, null, null, emailFromClient, "Ana", "Gomez");
    }

    private static SocialLoginRequestDTO facebook(String accessToken, String emailFromClient) {
        return new SocialLoginRequestDTO("FACEBOOK", null, accessToken, null, null, null, null, null, emailFromClient, "Ana", "Gomez");
    }

    private void stubGoogleAccessToken(String aud, String userinfo) throws Exception {
        doReturn(json("{\"aud\":\"" + aud + "\",\"azp\":\"" + aud + "\",\"sub\":\"g-1\"}"))
                .when(service).getJson(argThat(u -> u != null && u.contains("tokeninfo?access_token=")), isNull());
        doReturn(json(userinfo)).when(service).getJson(argThat(u -> u != null && u.contains("oauth2/v3/userinfo")), argThat(b -> b != null));
    }

    @Test
    void googleAccessTokenIssuedToAnotherAppIsRejected() throws Exception {
        stubGoogleAccessToken("evil-app.apps.googleusercontent.com", "{\"sub\":\"g-1\",\"email\":\"victima@gmail.com\",\"email_verified\":true}");

        assertThatThrownBy(() -> service.verifyAndExtract(google("ya29.token", null)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Audiencia");
    }

    @Test
    void googleAccessTokenOfOurAppLogsInWithTheProviderEmail() throws Exception {
        stubGoogleAccessToken("456-android.apps.googleusercontent.com", "{\"sub\":\"g-1\",\"email\":\"ana@gmail.com\",\"email_verified\":true,\"given_name\":\"Ana\"}");

        OAuthUserInfo info = service.verifyAndExtract(google("ya29.token", "admin@drivique.com"));

        assertThat(info.email()).isEqualTo("ana@gmail.com");
    }

    @Test
    void googleProfileWithoutEmailNeverFallsBackToTheEmailSentByTheClient() throws Exception {
        stubGoogleAccessToken(OUR_GOOGLE_CLIENT, "{\"sub\":\"g-1\"}");

        assertThatThrownBy(() -> service.verifyAndExtract(google("ya29.token", "admin@drivique.com")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void googleWithoutConfiguredClientIdIsRefusedInProtectedEnvironments() throws Exception {
        ReflectionTestUtils.setField(service, "googleClientId", "");
        stubGoogleAccessToken(OUR_GOOGLE_CLIENT, "{\"sub\":\"g-1\",\"email\":\"ana@gmail.com\"}");

        assertThatThrownBy(() -> service.verifyAndExtract(google("ya29.token", null)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("no configurado");
    }

    private void stubFacebook(String appId, boolean valid, String me) throws Exception {
        doReturn(json("{\"data\":{\"is_valid\":" + valid + ",\"app_id\":\"" + appId + "\"}}"))
                .when(service).getJson(argThat(u -> u != null && u.contains("debug_token")), isNull());
        doReturn(json(me)).when(service).getJson(argThat(u -> u != null && u.contains("graph.facebook.com/me")), isNull());
    }

    @Test
    void facebookTokenOfAnotherAppIsRejected() throws Exception {
        stubFacebook("111", true, "{\"id\":\"f-1\",\"email\":\"victima@gmail.com\"}");

        assertThatThrownBy(() -> service.verifyAndExtract(facebook("EAAtoken", null)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("otra aplicación");
    }

    @Test
    void invalidFacebookTokenIsRejected() throws Exception {
        stubFacebook("999", false, "{\"id\":\"f-1\"}");

        assertThatThrownBy(() -> service.verifyAndExtract(facebook("EAAtoken", null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void facebookWithoutEmailUsesAnInternalAddressNeverTheOneSentByTheClient() throws Exception {
        stubFacebook("999", true, "{\"id\":\"f-1\",\"first_name\":\"Ana\"}");

        OAuthUserInfo info = service.verifyAndExtract(facebook("EAAtoken", "admin@drivique.com"));

        assertThat(info.email()).isEqualTo("fb.f-1@facebook.drivique.com");
    }

    @Test
    void facebookWithoutAppSecretIsRefusedInProtectedEnvironments() {
        ReflectionTestUtils.setField(service, "facebookAppSecret", "");

        assertThatThrownBy(() -> service.verifyAndExtract(facebook("EAAtoken", null)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("no configurado");
    }
}
