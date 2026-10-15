package com.drivique.api.service;

import com.drivique.api.config.Auditable;
import com.drivique.api.dto.SecuritySettingDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.SecurityConfiguration;
import com.drivique.api.model.SecurityConfigurationChange;
import com.drivique.api.model.User;
import com.drivique.api.repository.SecurityConfigurationChangeRepository;
import com.drivique.api.repository.SecurityConfigurationRepository;
import com.drivique.api.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Parámetros globales de seguridad: se validan, se auditan y el último cambio puede revertirse. */
@Service
public class SecuritySettingsAdminService {

    private record Rule(String type, Integer min, Integer max) {}

    /** Solo estos parámetros son editables; cualquier otra llave se rechaza. */
    private static final Map<String, Rule> RULES = Map.of(
            "MAX_LOGIN_ATTEMPTS", new Rule("INTEGER", 3, 10),
            "ACCOUNT_LOCKOUT_MINUTES", new Rule("INTEGER", 5, 1440),
            "SESSION_IDLE_TIMEOUT_MINUTES", new Rule("INTEGER", 5, 480),
            "PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES", new Rule("INTEGER", 5, 120),
            "MFA_REQUIRED_FOR_ADMINS", new Rule("BOOLEAN", null, null));

    private final SecurityConfigurationRepository settings;
    private final SecurityConfigurationChangeRepository changes;
    private final UserRepository users;

    public SecuritySettingsAdminService(SecurityConfigurationRepository settings, SecurityConfigurationChangeRepository changes,
                                        UserRepository users) {
        this.settings = settings;
        this.changes = changes;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<SecuritySettingDTO> list() {
        return settings.findAllByOrderByConfigKeyAsc().stream().filter(s -> RULES.containsKey(s.getConfigKey())).map(this::toDto).toList();
    }

    @Transactional
    @Auditable(domain = "CONFIGURATION", entity = "SecurityConfiguration", action = "UPDATE", description = "Cambio de un parámetro global", includeArgs = true, includeResult = false)
    public SecuritySettingDTO update(String key, String value, String reason, String actorEmail) {
        Rule rule = RULES.get(key);
        SecurityConfiguration setting = settings.findByConfigKey(key).filter(s -> rule != null)
                .orElseThrow(() -> new ResourceNotFoundException("Parámetro no editable: " + key));
        String clean = validate(key, rule, value);
        if (clean.equals(setting.getConfigValue())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El parámetro ya tiene ese valor.");
        }
        String old = setting.getConfigValue();
        setting.changeValue(clean);
        changes.save(new SecurityConfigurationChange(key, old, clean, trim(reason), false, actor(actorEmail)));
        return toDto(settings.save(setting));
    }

    /** Política de reversión: solo el último cambio, una vez; la reversión queda registrada como un cambio más. */
    @Transactional
    @Auditable(domain = "CONFIGURATION", entity = "SecurityConfiguration", action = "UPDATE", description = "Reversión de un parámetro global", includeArgs = true, includeResult = false)
    public SecuritySettingDTO revert(String key, String actorEmail) {
        SecurityConfiguration setting = settings.findByConfigKey(key).filter(s -> RULES.containsKey(key))
                .orElseThrow(() -> new ResourceNotFoundException("Parámetro no editable: " + key));
        SecurityConfigurationChange last = changes.findFirstByConfigKeyOrderByChangedAtDesc(key)
                .filter(c -> !c.isReverted() && c.getOldValue() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No hay un cambio reciente para revertir."));
        String current = setting.getConfigValue();
        last.markReverted();
        setting.changeValue(last.getOldValue());
        changes.save(new SecurityConfigurationChange(key, current, last.getOldValue(), "Reversión del cambio anterior", true, actor(actorEmail)));
        return toDto(settings.save(setting));
    }

    private String validate(String key, Rule rule, String value) {
        String text = value == null ? "" : value.trim();
        if ("BOOLEAN".equals(rule.type())) {
            if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El valor de " + key + " debe ser true o false.");
            }
            return text.toLowerCase();
        }
        int number;
        try {
            number = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El valor de " + key + " debe ser un número entero.");
        }
        if (number < rule.min() || number > rule.max()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El valor de " + key + " debe estar entre " + rule.min() + " y " + rule.max() + ".");
        }
        return String.valueOf(number);
    }

    private SecuritySettingDTO toDto(SecurityConfiguration s) {
        Rule rule = RULES.get(s.getConfigKey());
        SecurityConfigurationChange last = changes.findFirstByConfigKeyOrderByChangedAtDesc(s.getConfigKey()).orElse(null);
        boolean canRevert = last != null && !last.isReverted() && last.getOldValue() != null;
        return new SecuritySettingDTO(s.getConfigKey(), s.getConfigValue(), s.getDescription(), rule.type(), rule.min(), rule.max(),
                s.getUpdatedAt(), canRevert, canRevert ? last.getOldValue() : null, last != null ? last.getChangedBy().getFullName() : null);
    }

    private User actor(String email) {
        return users.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private static String trim(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }
}
