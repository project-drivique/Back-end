package com.drivique.api.config;

import com.drivique.api.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Aspect
@Component
public class AuditAspect {

    private static final Logger LOG = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String actorEmail = getCurrentUserEmail();
        String ipAddress = getClientIpAddress();
        String domain = auditable.domain();
        String entity = auditable.entity().isBlank() ? joinPoint.getSignature().getName() : auditable.entity();
        String action = auditable.action();
        String description = auditable.description().isBlank() ? "Operación " + action + " en " + entity : auditable.description();

        Object[] args = joinPoint.getArgs();
        UUID entityId = extractEntityId(args);
        Object oldData = (args != null && args.length > 0) ? args : null;

        try {
            Object result = joinPoint.proceed();

            try {
                auditService.recordAudit(
                        domain,
                        entity,
                        entityId,
                        action,
                        "SUCCESS",
                        actorEmail,
                        null,
                        ipAddress,
                        description,
                        oldData,
                        result
                );
            } catch (Exception auditEx) {
                LOG.error("Error al registrar bitácora de auditoría exitosa: {}", auditEx.getMessage());
            }

            return result;
        } catch (Throwable ex) {
            try {
                auditService.recordAudit(
                        domain,
                        entity,
                        entityId,
                        action,
                        "FAILURE",
                        actorEmail,
                        null,
                        ipAddress,
                        description + " - Error: " + ex.getMessage(),
                        oldData,
                        null
                );
            } catch (Exception auditEx) {
                LOG.error("Error al registrar bitácora de auditoría fallida: {}", auditEx.getMessage());
            }
            throw ex;
        }
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "system";
    }

    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isBlank() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
                    return xForwardedFor.split(",")[0].trim();
                }
                String xRealIp = request.getHeader("X-Real-IP");
                if (xRealIp != null && !xRealIp.isBlank() && !"unknown".equalsIgnoreCase(xRealIp)) {
                    return xRealIp.trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            LOG.debug("No se pudo obtener la IP del cliente: {}", e.getMessage());
        }
        return "127.0.0.1";
    }

    private UUID extractEntityId(Object[] args) {
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof UUID uuid) {
                    return uuid;
                }
            }
        }
        return null;
    }
}
