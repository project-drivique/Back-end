package com.drivique.api.config;

import com.drivique.api.service.email.EmailLanguage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * La web y la App envían el idioma que la persona está usando (X-App-Language). Con sesión iniciada se guarda en su perfil,
 * para que los correos que salen sin una petición (avisos programados, pagos confirmados) lleguen en ese idioma.
 */
public class LanguageHeaderInterceptor implements HandlerInterceptor {

    private final EmailLanguage emailLanguage;

    public LanguageHeaderInterceptor(EmailLanguage emailLanguage) {
        this.emailLanguage = emailLanguage;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(EmailLanguage.HEADER);
        if (header == null || header.isBlank()) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            emailLanguage.remember(authentication.getName(), header);
        }
        return true;
    }
}
