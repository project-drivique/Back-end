package com.drivique.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import com.drivique.api.service.CaptchaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Autowired(required = false)
    private CaptchaService captchaService;

    @Autowired(required = false)
    private com.drivique.api.service.email.EmailLanguage emailLanguage;

    /** El captcha se valida en el servidor antes de crear la retención de una reserva. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (emailLanguage != null) {
            registry.addInterceptor(new LanguageHeaderInterceptor(emailLanguage)).addPathPatterns("/v1/**");
        }
        if (captchaService == null) {
            return;
        }
        HandlerInterceptor captcha = new HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                     jakarta.servlet.http.HttpServletResponse response, Object handler) {
                if ("POST".equalsIgnoreCase(request.getMethod())) {
                    String forwarded = request.getHeader("X-Forwarded-For");
                    String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
                    captchaService.verify(request.getHeader("X-Captcha-Token"), ip);
                }
                return true;
            }
        };
        registry.addInterceptor(captcha).addPathPatterns("/v1/reservations/checkout/initiate");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        java.nio.file.Path path = Paths.get(uploadDir).toAbsolutePath().normalize();
        String location1 = "file:" + path.toString().replace('\\', '/') + "/";
        String location2 = path.toUri().toString();
        if (!location2.endsWith("/")) {
            location2 += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location1, location2);
    }
}
