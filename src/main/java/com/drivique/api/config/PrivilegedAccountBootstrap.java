package com.drivique.api.config;

import com.drivique.api.model.User;
import com.drivique.api.repository.UserRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Las semillas de la base de datos crean al super administrador y a los encargados de sucursal con la
 * contraseña bloqueada: ninguna clave vive en el repositorio. Aquí se les asigna la contraseña real a partir de
 * variables de entorno (BOOTSTRAP_ADMIN_PASSWORD y BOOTSTRAP_BRANCH_ADMIN_PASSWORD).
 *
 * Solo toca cuentas que sigan bloqueadas o con una clave que estuvo publicada; nunca pisa una contraseña que
 * una persona ya cambió.
 */
@Component
public class PrivilegedAccountBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PrivilegedAccountBootstrap.class);

    public static final String LOCKED_MARKER = "!LOCKED-DEFINE-BOOTSTRAP-PASSWORD";
    static final int MIN_PASSWORD_LENGTH = 12;

    /** Contraseñas que estuvieron escritas en comentarios o documentación del repositorio. */
    private static final List<String> PUBLISHED_PASSWORDS = List.of("DriviqueAdmin2026!", "Admin123*", "Cliente123*");

    /** Hashes que estuvieron versionados en las semillas. */
    private static final Set<String> PUBLISHED_HASHES = Set.of(
            "$2a$12$iIrC/bchcMQVAN7jkJSuTOGvGKtorg5NKnP3qRcXpsdEHM5jP02BS",
            "$2a$12$e8wF3QvY3bJ1mD1P2Q4c3eB7vH9aX0zW8yV7uT6sR5qP4oN3mL2kK",
            "$2a$12$ohhMKXdkbVYLg6y9/X.7gOXIkbXqnxNl2LO6KOM2m/2vKnttSGfzS"
    );

    static final String SUPER_ADMIN_EMAIL = "admin@drivique.com";
    static final List<String> BRANCH_ADMIN_EMAILS = List.of(
            "encargado.bogota@drivique.com",
            "encargado.medellin.centro@drivique.com",
            "encargado.medellin.aeropuerto@drivique.com",
            "encargado.cali@drivique.com",
            "encargado.barranquilla@drivique.com",
            "encargado.cartagena@drivique.com"
    );

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String adminPassword;
    private final String branchAdminPassword;
    private final boolean requireSecrets;

    public PrivilegedAccountBootstrap(
            UserRepository users,
            PasswordEncoder encoder,
            @Value("${BOOTSTRAP_ADMIN_PASSWORD:${ADMIN_INITIAL_PASSWORD:}}") String adminPassword,
            @Value("${BOOTSTRAP_BRANCH_ADMIN_PASSWORD:${BRANCH_ADMIN_INITIAL_PASSWORD:}}") String branchAdminPassword,
            @Value("${app.security.require-secrets:false}") boolean requireSecrets
    ) {
        this.users = users;
        this.encoder = encoder;
        this.adminPassword = adminPassword;
        this.branchAdminPassword = branchAdminPassword;
        this.requireSecrets = requireSecrets;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            apply(SUPER_ADMIN_EMAIL, adminPassword, "BOOTSTRAP_ADMIN_PASSWORD");
            BRANCH_ADMIN_EMAILS.forEach(email -> apply(email, branchAdminPassword, "BOOTSTRAP_BRANCH_ADMIN_PASSWORD"));
        } catch (RuntimeException ex) {
            // La base puede no estar lista o no tener estas cuentas: no debe impedir el arranque
            LOG.warn("No se pudo revisar las cuentas privilegiadas: {}", ex.getMessage());
        }
    }

    void apply(String email, String configuredPassword, String variableName) {
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !needsPassword(user.getPasswordHash())) {
            return;
        }
        if (isStrong(configuredPassword)) {
            user.setPasswordHash(encoder.encode(configuredPassword));
            users.save(user);
            LOG.info("Contraseña de {} definida desde {}.", email, variableName);
            return;
        }
        if (!requireSecrets && !isLocked(user.getPasswordHash())) {
            // Base local ya existente con una clave conocida: no se toca para no cortar el trabajo diario, pero se avisa
            LOG.warn("[DESARROLLO] {} conserva una contraseña publicada. Define {} para reemplazarla (en qa/main se reemplaza siempre).", email, variableName);
            return;
        }
        if (!requireSecrets) {
            String temporary = randomPassword();
            user.setPasswordHash(encoder.encode(temporary));
            users.save(user);
            LOG.warn("[DESARROLLO] {} no tenía contraseña segura. Contraseña temporal generada (solo en este arranque): {}", email, temporary);
            return;
        }
        if (!LOCKED_MARKER.equals(user.getPasswordHash())) {
            user.setPasswordHash(LOCKED_MARKER);
            users.save(user);
        }
        LOG.warn("[SEGURIDAD] La cuenta {} está bloqueada hasta definir {} (mínimo {} caracteres).", email, variableName, MIN_PASSWORD_LENGTH);
    }

    private static boolean isLocked(String hash) {
        return hash == null || hash.isBlank() || LOCKED_MARKER.equals(hash);
    }

    boolean needsPassword(String hash) {
        if (hash == null || hash.isBlank() || LOCKED_MARKER.equals(hash) || PUBLISHED_HASHES.contains(hash)) {
            return true;
        }
        return PUBLISHED_PASSWORDS.stream().anyMatch(known -> {
            try {
                return encoder.matches(known, hash);
            } catch (RuntimeException ex) {
                return false;
            }
        });
    }

    static boolean isStrong(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH && !PUBLISHED_PASSWORDS.contains(password);
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
