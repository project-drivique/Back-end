package com.drivique.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * La API prohíbe ser incrustada en cualquier frame (X-Frame-Options: DENY, frame-ancestors 'none'), pero el panel
 * de los encargados muestra el documento del cliente (PDF) dentro de un visor. Solo para los archivos KYC se permite
 * que lo incruste el propio sitio y los orígenes del frontend; el resto de la API sigue sin poder incrustarse.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class KycFramingHeaderFilter extends OncePerRequestFilter {

    private static final Pattern KYC_FILE = Pattern.compile("^.*/kyc/documents/(file|[^/]+/file)/?$");

    private final String frameAncestors;

    public KycFramingHeaderFilter(
            @Value("${app.frontend-url:}") String frontendUrl,
            @Value("${app.cors.allowed-origins:}") String allowedOrigins
    ) {
        Set<String> origins = new LinkedHashSet<>();
        origins.add("'self'");
        for (String origin : (frontendUrl + "," + allowedOrigins).split(",")) {
            String clean = origin.trim().replaceAll("/+$", "");
            if (clean.startsWith("http://") || clean.startsWith("https://")) {
                origins.add(clean);
            }
        }
        this.frameAncestors = String.join(" ", origins);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equalsIgnoreCase(request.getMethod()) || !KYC_FILE.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Spring Security no sobrescribe cabeceras que ya existen en la respuesta
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("Content-Security-Policy", "default-src 'self'; frame-ancestors " + frameAncestors + "; script-src 'self' 'unsafe-inline'");
        chain.doFilter(request, response);
    }
}
