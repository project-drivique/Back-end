package com.drivique.api.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.drivique.api.model.BrandConfiguration;
import com.drivique.api.model.Language;
import com.drivique.api.model.User;
import com.drivique.api.model.UserPreference;
import com.drivique.api.repository.BrandRepository;
import com.drivique.api.repository.LanguageRepository;
import com.drivique.api.repository.UserPreferenceRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.service.AuthEmailService;
import com.drivique.api.service.BrandEmailTheme;
import com.drivique.api.service.email.EmailLanguage;
import com.drivique.api.service.email.EmailTexts;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class EmailLocalizationTests {

    private final UserRepository users = mock(UserRepository.class);
    private final UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
    private final LanguageRepository languages = mock(LanguageRepository.class);
    private final EmailLanguage language = new EmailLanguage(users, preferences, languages);
    private final EmailTexts texts = new EmailTexts();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void platformLanguageCodesMapToTheEmailLocales() {
        assertThat(EmailLanguage.fromCode("es")).isEqualTo(Locale.forLanguageTag("es"));
        assertThat(EmailLanguage.fromCode("en-US")).isEqualTo(Locale.ENGLISH);
        assertThat(EmailLanguage.fromCode("pt")).isEqualTo(Locale.forLanguageTag("pt"));
        assertThat(EmailLanguage.fromCode("br")).isEqualTo(Locale.forLanguageTag("pt-BR"));
        assertThat(EmailLanguage.fromCode("pt_BR")).isEqualTo(Locale.forLanguageTag("pt-BR"));
        assertThat(EmailLanguage.fromCode("fr")).isEqualTo(Locale.FRENCH);
        assertThat(EmailLanguage.fromCode("de")).isNull();
        assertThat(EmailLanguage.fromCode(" ")).isNull();
        assertThat(EmailLanguage.toCode(Locale.forLanguageTag("pt-BR"))).isEqualTo("br");
        assertThat(EmailLanguage.toCode(Locale.FRENCH)).isEqualTo("fr");
    }

    @Test
    void theSavedLanguageOfTheUserWinsOverTheRequest() {
        UUID languageId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        UserPreference preference = mock(UserPreference.class);
        when(preference.getLanguageId()).thenReturn(languageId);
        Language french = mock(Language.class);
        when(french.getCode()).thenReturn("fr");
        when(users.findByEmailIgnoreCaseAndDeletedAtIsNull("ana@drivique.com")).thenReturn(Optional.of(user));
        when(preferences.findByUserId(user.getId())).thenReturn(Optional.of(preference));
        when(languages.findById(languageId)).thenReturn(Optional.of(french));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(EmailLanguage.HEADER, "en");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(language.forEmail("ana@drivique.com")).isEqualTo(Locale.FRENCH);
    }

    @Test
    void withoutASavedLanguageTheRequestHeaderIsUsedAndThenSpanish() {
        when(users.findByEmailIgnoreCaseAndDeletedAtIsNull("nuevo@drivique.com")).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(EmailLanguage.HEADER, "br");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(language.forEmail("nuevo@drivique.com")).isEqualTo(Locale.forLanguageTag("pt-BR"));

        RequestContextHolder.resetRequestAttributes();
        assertThat(language.forEmail("nuevo@drivique.com")).isEqualTo(EmailLanguage.DEFAULT);

        MockHttpServletRequest browserOnly = new MockHttpServletRequest();
        browserOnly.addHeader("Accept-Language", "fr-CA,fr;q=0.9,en;q=0.8");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(browserOnly));
        assertThat(language.fromRequest()).isEqualTo(Locale.FRENCH);
    }

    @Test
    void theLanguageInUseIsSavedInTheProfileOnlyWhenItChanges() {
        UUID id = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        Language english = mock(Language.class);
        UUID englishId = UUID.randomUUID();
        when(english.getId()).thenReturn(englishId);
        when(users.findByEmailIgnoreCaseAndDeletedAtIsNull("ana@drivique.com")).thenReturn(Optional.of(user));
        when(languages.findByCodeIgnoreCase("en")).thenReturn(Optional.of(english));
        UserPreference preference = new UserPreference(user);
        when(preferences.findByUserId(id)).thenReturn(Optional.of(preference));

        language.remember("ana@drivique.com", "en");
        language.remember("ana@drivique.com", "en");

        assertThat(preference.getLanguageId()).isEqualTo(englishId);
        verify(preferences, org.mockito.Mockito.times(1)).save(preference);
    }

    @Test
    void everyLanguageHasEveryKeyOfTheBaseBundle() throws Exception {
        Properties base = load("email/email.properties");
        for (String file : List.of("email_en", "email_pt", "email_pt_BR", "email_fr")) {
            Properties other = load("email/" + file + ".properties");
            assertThat(other.stringPropertyNames()).as(file + " debe tener las mismas claves que el español")
                    .containsExactlyInAnyOrderElementsOf(base.stringPropertyNames());
            for (String key : base.stringPropertyNames()) {
                // Los marcadores deben conservarse: un texto traducido sin {code} o {number} dejaría el correo sin dato
                for (String marker : List.of("{code}", "{number}", "{name}", "{reservation}", "{pin}", "{confirm}", "{reject}", "{day}", "{purpose}")) {
                    assertThat(other.getProperty(key).contains(marker)).as(file + " / " + key + " / " + marker)
                            .isEqualTo(base.getProperty(key).contains(marker));
                }
            }
        }
    }

    @Test
    void textsComeInTheRequestedLanguageWithFallbackToSpanish() {
        assertThat(texts.get(Locale.forLanguageTag("es"), "otp.title")).isEqualTo("Código de Verificación");
        assertThat(texts.get(Locale.ENGLISH, "otp.title")).isEqualTo("Verification Code");
        assertThat(texts.get(Locale.forLanguageTag("pt-BR"), "contract.button")).isEqualTo("Ver minhas reservas");
        assertThat(texts.get(Locale.forLanguageTag("pt"), "contract.button")).isEqualTo("Ver as minhas reservas");
        assertThat(texts.get(Locale.FRENCH, "otp.ignore")).contains("n'avez pas demandé");
        // Un idioma que no existe cae al español, no al idioma del servidor
        assertThat(texts.get(Locale.GERMAN, "otp.title")).isEqualTo("Código de Verificación");
        assertThat(texts.get(Locale.ENGLISH, "otp.subject", "code", "123456")).isEqualTo("Drivique - Your security code: 123456");
    }

    @Test
    void buttonsWrittenInSpanishByTheServicesAreTranslated() {
        assertThat(texts.actionLabel(Locale.ENGLISH, "Ver mi reserva")).isEqualTo("View my reservation");
        assertThat(texts.actionLabel(Locale.FRENCH, "Continuar y Firmar Contrato")).isEqualTo("Continuer et signer le contrat");
        assertThat(texts.actionLabel(Locale.ENGLISH, "Un botón que no conocemos")).isEqualTo("Un botón que no conocemos");
    }

    @Test
    void theVerificationEmailIsSentInTheLanguageOfTheRecipientAndWithTheBrandName() throws Exception {
        JavaMailSenderImpl real = new JavaMailSenderImpl();
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = real.createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        BrandRepository brands = mock(BrandRepository.class);
        BrandConfiguration brand = mock(BrandConfiguration.class);
        when(brand.getCompanyName()).thenReturn("BTS");
        when(brand.getPrimaryColor()).thenReturn("#b45309");
        when(brand.getSecondaryColor()).thenReturn("#78350f");
        when(brand.getAccentColor()).thenReturn("#f59e0b");
        when(brands.findByActiveTrue()).thenReturn(Optional.of(brand));
        BrandEmailTheme theme = new BrandEmailTheme(brands);
        EmailTexts brandedTexts = new EmailTexts();
        ReflectionTestUtils.setField(brandedTexts, "brandTheme", theme);
        EmailLanguage english = mock(EmailLanguage.class);
        when(english.forEmail("ana@drivique.com")).thenReturn(Locale.ENGLISH);

        AuthEmailService service = new AuthEmailService(provider, "no-reply@drivique.com");
        ReflectionTestUtils.setField(service, "brandTheme", theme);
        ReflectionTestUtils.setField(service, "emailLanguage", english);
        ReflectionTestUtils.setField(service, "emailTexts", brandedTexts);

        service.sendOtp("ana@drivique.com", AuthEmailService.Purpose.PASSWORD_RESET, "654321");

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        String subject = sent.getValue().getSubject();
        String html = new String(extractHtml(sent.getValue()).getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        assertThat(subject).isEqualTo("BTS - Your security code: 654321");
        assertThat(html).contains("Verification Code").contains("reset your password").contains("on the BTS platform").contains("654321");
        assertThat(html).contains(">BTS</h1>").doesNotContain("Código de Verificación");
        // La marca también cambió los colores: el azul original ya no está
        assertThat(html).doesNotContain("#1e3a8a").contains("#78350f");
        verify(english, org.mockito.Mockito.atLeastOnce()).forEmail(any());
    }

    @Test
    void theBrandNameReplacesTheWordButNotAddressesOrEmails() {
        BrandRepository brands = mock(BrandRepository.class);
        BrandConfiguration brand = mock(BrandConfiguration.class);
        when(brand.getCompanyName()).thenReturn("BTS");
        when(brand.getPrimaryColor()).thenReturn("#b45309");
        when(brand.getSecondaryColor()).thenReturn("#78350f");
        when(brand.getAccentColor()).thenReturn("#f59e0b");
        when(brands.findByActiveTrue()).thenReturn(Optional.of(brand));
        BrandEmailTheme theme = new BrandEmailTheme(brands);

        String out = theme.apply("<h1>DRIVIQUE</h1><p>Gracias por elegir Drivique. Escríbenos a soporte@drivique.com o visita https://drivique.tech/reservas. © 2026 Drivique Mobility</p>"
                + "<body style=\"background-color: #0b1329\">");

        assertThat(out).contains(">BTS</h1>").contains("elegir BTS.").contains("© 2026 BTS Mobility");
        assertThat(out).contains("soporte@drivique.com").contains("https://drivique.tech/reservas");
        assertThat(out).doesNotContain("#0b1329");
        assertThat(theme.companyName()).isEqualTo("BTS");
    }

    private static Properties load(String path) throws Exception {
        Properties properties = new Properties();
        try (InputStream in = EmailLocalizationTests.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static String extractHtml(MimeMessage message) throws Exception {
        Object content = message.getContent();
        return flatten(content);
    }

    private static String flatten(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof jakarta.mail.Multipart multipart) {
            StringBuilder all = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                all.append(flatten(multipart.getBodyPart(i).getContent())).append('\n');
            }
            return all.toString();
        }
        return "";
    }
}
