package com.drivique.api.config;

import com.drivique.api.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final List<String> allowedOrigins;

    public SecurityConfig(
            @Value("${app.cors.allowed-origins:}") String allowedOrigins
    ) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Bean
    @org.springframework.core.annotation.Order(1)
    SecurityFilterChain uploadsSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/uploads/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.disable())
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self' data: blob:; frame-ancestors *; script-src 'self' 'unsafe-inline'"))
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/uploads/kyc/**").denyAll()
                        .anyRequest().permitAll()
                )
                .build();
    }

    @Bean
    @org.springframework.core.annotation.Order(2)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ProblemSecurityHandler problems,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        // DENY en toda la API salvo los archivos KYC, que el panel de encargados muestra en un visor
                        // (KycFramingHeaderFilter les pone SAMEORIGIN y frame-ancestors del frontend)
                        .frameOptions(frame -> frame.disable())
                        .addHeaderWriter(new org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter(
                                new org.springframework.security.web.util.matcher.NegatedRequestMatcher(
                                        request -> request.getRequestURI().matches("^.*/kyc/documents/(file|[^/]+/file)/?$")),
                                new org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter(
                                        org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter.XFrameOptionsMode.DENY)))
                        .contentTypeOptions(contentType -> {})
                        .xssProtection(xss -> xss.headerValue(
                                org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; frame-ancestors 'none'; script-src 'self' 'unsafe-inline'"))
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/uploads/**",
                                "/actuator/health",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/v1/payments/wompi/webhook",
                                "/v1/auth/register",
                                "/v1/auth/login",
                                "/v1/auth/social/login",
                                "/v1/auth/google",
                                "/v1/auth/facebook",
                                "/v1/auth/refresh",
                                "/v1/auth/logout",
                                "/v1/auth/verify-email",
                                "/v1/auth/resend-verification",
                                "/v1/auth/forgot-password",
                                "/v1/auth/validate-reset-code",
                                "/v1/auth/reset-password"
                        ).permitAll()
                        .requestMatchers("/v1/public/deliveries/action").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/v1/languages",
                                "/v1/currencies",
                                "/v1/exchange-rates/latest",
                                "/v1/brand-configurations/active",
                                "/v1/security-configurations",
                                "/v1/captcha/config",
                                "/v1/kyc/document-types",
                                "/v1/departments",
                                "/v1/departments/*/cities",
                                "/v1/cities",
                                "/v1/cities/*/branches",
                                "/v1/branches",
                                "/v1/branches/*",
                                "/v1/branches/*/reviews",
                                "/v1/vehicle-brands",
                                "/v1/transmission-types",
                                "/v1/fuel-types",
                                "/v1/vehicle-statuses",
                                "/v1/vehicle-categories",
                                "/v1/vehicle-categories/*",
                                "/v1/vehicles/**",
                                "/v1/vehicles/*/reviews",
                                "/v1/features",
                                "/v1/features/**",
                                "/v1/additional-services",
                                "/v1/insurance-coverages",
                                "/v1/mileage-plans",
                                "/v1/promotions/featured",
                                "/v1/promotions/published",
                                "/v1/contracts/clauses"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type", "Location", "X-Total-Count", "Idempotency-Key", "Idempotent-Replayed"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
