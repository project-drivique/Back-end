package com.drivique.api.service;

import com.drivique.api.model.AuditLog;
import com.drivique.api.model.User;
import com.drivique.api.repository.AuditLogRepository;
import com.drivique.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Servicio de seguridad para auditar y securizar las cuentas operativas (Super Admin y Encargados)
 * evitando que permanezcan con credenciales de prueba conocidas en producción.
 */
@Service
public class AdminSecurityBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityBootstrapService.class);

    // Hashes públicos conocidos provenientes de las semillas de desarrollo
    private static final Set<String> KNOWN_PUBLIC_SEED_HASHES = Set.of(
            "$2a$12$e8wF3QvY3bJ1mD1P2Q4c3eB7vH9aX0zW8yV7uT6sR5qP4oN3mL2kK",
            "$2a$12$iIrC/bchcMQVAN7jkJSuTOGvGKtorg5NKnP3qRcXpsdEHM5jP02BS",
            "$2a$12$ohhMKXdkbVYLg6y9/X.7gOXIkbXqnxNl2LO6KOM2m/2vKnttSGfzS"
    );

    private static final List<String> OPERATIONAL_EMAILS = List.of(
            "admin@drivique.com",
            "encargado.bogota@drivique.com",
            "encargado.medellin.centro@drivique.com",
            "encargado.medellin.aeropuerto@drivique.com",
            "encargado.cali@drivique.com",
            "encargado.barranquilla@drivique.com",
            "encargado.cartagena@drivique.com"
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogRepository auditLogRepository;
    private final String adminEnvPassword;
    private final String branchAdminEnvPassword;

    public AdminSecurityBootstrapService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditLogRepository auditLogRepository,
            @Value("${app.security.admin-initial-password:${ADMIN_INITIAL_PASSWORD:}}") String adminEnvPassword,
            @Value("${app.security.branch-admin-initial-password:${BRANCH_ADMIN_INITIAL_PASSWORD:}}") String branchAdminEnvPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogRepository = auditLogRepository;
        this.adminEnvPassword = adminEnvPassword;
        this.branchAdminEnvPassword = branchAdminEnvPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void secureOperationalAccounts() {
        // 1. Si se configuraron contraseñas seguras por variable de entorno, aplicarlas inmediatamente
        if (adminEnvPassword != null && !adminEnvPassword.isBlank()) {
            userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("admin@drivique.com").ifPresent(admin -> {
                admin.setPasswordHash(passwordEncoder.encode(adminEnvPassword.trim()));
                userRepository.save(admin);
                log.info("[SEGURIDAD OPERATIVA] Contraseña de Super Admin actualizada exitosamente desde variable de entorno.");
            });
        }

        if (branchAdminEnvPassword != null && !branchAdminEnvPassword.isBlank()) {
            for (String email : OPERATIONAL_EMAILS) {
                if ("admin@drivique.com".equalsIgnoreCase(email)) continue;
                userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).ifPresent(manager -> {
                    manager.setPasswordHash(passwordEncoder.encode(branchAdminEnvPassword.trim()));
                    userRepository.save(manager);
                });
            }
            log.info("[SEGURIDAD OPERATIVA] Contraseñas de Encargados de Sucursal actualizadas exitosamente desde variable de entorno.");
        }

        // 2. Verificar si alguna cuenta operativa aún conserva un hash conocido de los seeds
        for (String email : OPERATIONAL_EMAILS) {
            userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).ifPresent(user -> {
                if (KNOWN_PUBLIC_SEED_HASHES.contains(user.getPasswordHash())) {
                    String warningMsg = "ADVERTENCIA DE SEGURIDAD: La cuenta operativa (" + email + ") conserva la clave por defecto de la semilla. Debe cambiarse en ambientes productivos.";
                    log.warn("[AUDITORÍA SEGURIDAD] {}", warningMsg);
                    try {
                        auditLogRepository.save(new AuditLog(
                                "SECURITY",
                                "User",
                                user.getId(),
                                "UPDATE",
                                "FAILURE",
                                null,
                                null,
                                "127.0.0.1",
                                warningMsg,
                                null,
                                null
                        ));
                    } catch (Exception ignored) {
                    }
                }
            });
        }
    }
}
