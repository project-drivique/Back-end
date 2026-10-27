package com.drivique.api.service.email;

import com.drivique.api.model.Language;
import com.drivique.api.model.User;
import com.drivique.api.model.UserPreference;
import com.drivique.api.repository.LanguageRepository;
import com.drivique.api.repository.UserPreferenceRepository;
import com.drivique.api.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Idioma en el que se escribe un correo: el que la persona tiene guardado en su perfil; si no hay, el de la
 * petición en curso (X-App-Language que envían la web y la App, o Accept-Language); y si tampoco, español.
 * Los idiomas son los de la plataforma: es, en, pt, br (portugués de Brasil) y fr.
 */
@Component
public class EmailLanguage {

    public static final Locale DEFAULT = Locale.forLanguageTag("es");
    public static final String HEADER = "X-App-Language";

    private static final Logger LOG = LoggerFactory.getLogger(EmailLanguage.class);

    private final UserRepository users;
    private final UserPreferenceRepository preferences;
    private final LanguageRepository languages;
    /** Último idioma guardado por correo, para no consultar la base en cada petición. */
    private final Map<String, String> remembered = new ConcurrentHashMap<>();

    public EmailLanguage(UserRepository users, UserPreferenceRepository preferences, LanguageRepository languages) {
        this.users = users;
        this.preferences = preferences;
        this.languages = languages;
    }

    @Transactional(readOnly = true)
    public Locale forEmail(String email) {
        try {
            if (email != null && !email.isBlank()) {
                Locale saved = users.findByEmailIgnoreCaseAndDeletedAtIsNull(email.trim())
                        .flatMap(user -> preferences.findByUserId(user.getId()))
                        .map(UserPreference::getLanguageId)
                        .flatMap(languages::findById)
                        .map(Language::getCode)
                        .map(EmailLanguage::fromCode)
                        .orElse(null);
                if (saved != null) {
                    return saved;
                }
            }
        } catch (RuntimeException ex) {
            LOG.debug("No se pudo leer el idioma guardado de {}: {}", email, ex.getMessage());
        }
        return fromRequest();
    }

    /** Idioma que pide la petición en curso; fuera de una petición (tareas programadas) es el español. */
    public Locale fromRequest() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                HttpServletRequest request = attributes.getRequest();
                Locale locale = fromCode(request.getHeader(HEADER));
                if (locale == null) {
                    String accept = request.getHeader("Accept-Language");
                    if (accept != null && !accept.isBlank()) {
                        locale = fromCode(accept.split(",")[0].split(";")[0]);
                    }
                }
                if (locale != null) {
                    return locale;
                }
            }
        } catch (RuntimeException ex) {
            LOG.debug("No se pudo leer el idioma de la petición: {}", ex.getMessage());
        }
        return DEFAULT;
    }

    /**
     * Guarda en el perfil el idioma con el que la persona está usando la web o la App, para que los correos que se envían
     * sin una petición (avisos programados, pagos confirmados por Wompi) salgan en su idioma. Nunca interrumpe la petición.
     */
    @Transactional
    public void remember(String email, String headerCode) {
        try {
            Locale locale = fromCode(headerCode);
            if (locale == null || email == null || email.isBlank()) {
                return;
            }
            String code = toCode(locale);
            String key = email.trim().toLowerCase(Locale.ROOT);
            if (code.equals(remembered.get(key))) {
                return;
            }
            Optional<User> user = users.findByEmailIgnoreCaseAndDeletedAtIsNull(email.trim());
            Optional<Language> language = languages.findByCodeIgnoreCase(code);
            if (user.isEmpty() || language.isEmpty()) {
                return;
            }
            UserPreference preference = preferences.findByUserId(user.get().getId()).orElseGet(() -> new UserPreference(user.get()));
            if (!language.get().getId().equals(preference.getLanguageId())) {
                preference.setLanguageId(language.get().getId());
                preferences.save(preference);
            }
            remembered.put(key, code);
        } catch (RuntimeException ex) {
            LOG.debug("No se pudo recordar el idioma de {}: {}", email, ex.getMessage());
        }
    }

    /** es, en, pt, br (o variantes como pt-BR, en-US, fr_CA) a un Locale de los correos; null si no es un idioma soportado. */
    public static Locale fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.equals("br") || normalized.startsWith("pt-br")) {
            return Locale.forLanguageTag("pt-BR");
        }
        String language = normalized.split("-")[0];
        return switch (language) {
            case "es" -> DEFAULT;
            case "en" -> Locale.ENGLISH;
            case "pt" -> Locale.forLanguageTag("pt");
            case "fr" -> Locale.FRENCH;
            default -> null;
        };
    }

    /** Código con el que la plataforma guarda el idioma (el portugués de Brasil se guarda como br). */
    public static String toCode(Locale locale) {
        if ("pt".equals(locale.getLanguage()) && "BR".equals(locale.getCountry())) {
            return "br";
        }
        return locale.getLanguage();
    }
}
