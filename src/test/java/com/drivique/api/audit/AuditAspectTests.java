package com.drivique.api.audit;

import com.drivique.api.config.AuditAspect;
import com.drivique.api.config.Auditable;
import com.drivique.api.service.AuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAspectTests {

    @Mock
    private AuditService auditService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    private AuditAspect auditAspect;

    @BeforeEach
    void setUp() {
        auditAspect = new AuditAspect(auditService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "auditor@drivique.com",
                        "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void auditMethod_SuccessfulExecution_RecordsSuccessAuditLog() throws Throwable {
        Auditable auditable = createAuditable("FLEET", "Vehicle", "UPDATE", "Edición de vehículo");
        UUID entityId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getArgs()).thenReturn(new Object[]{entityId, "Updated Model"});
        when(joinPoint.proceed()).thenReturn("RESULT_OK");

        Object result = auditAspect.auditMethod(joinPoint, auditable);

        assertThat(result).isEqualTo("RESULT_OK");
        verify(auditService).recordAudit(
                eq("FLEET"),
                eq("Vehicle"),
                eq(entityId),
                eq("UPDATE"),
                eq("SUCCESS"),
                eq("auditor@drivique.com"),
                isNull(),
                eq("203.0.113.195"),
                eq("Edición de vehículo"),
                any(),
                eq("RESULT_OK")
        );
    }

    @Test
    void auditMethod_FailedExecution_RecordsFailureAuditLogAndReThrows() throws Throwable {
        Auditable auditable = createAuditable("IAM", "User", "DELETE", "Eliminar usuario");
        UUID entityId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "198.51.100.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(joinPoint.getArgs()).thenReturn(new Object[]{entityId});
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("Cannot delete active user"));

        assertThatThrownBy(() -> auditAspect.auditMethod(joinPoint, auditable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot delete active user");

        verify(auditService).recordAudit(
                eq("IAM"),
                eq("User"),
                eq(entityId),
                eq("DELETE"),
                eq("FAILURE"),
                eq("auditor@drivique.com"),
                isNull(),
                eq("198.51.100.1"),
                contains("Cannot delete active user"),
                any(),
                isNull()
        );
    }

    private Auditable createAuditable(String domain, String entity, String action, String description) {
        return new Auditable() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Auditable.class;
            }

            @Override
            public String domain() {
                return domain;
            }

            @Override
            public String entity() {
                return entity;
            }

            @Override
            public String action() {
                return action;
            }

            @Override
            public String description() {
                return description;
            }
        };
    }
}
