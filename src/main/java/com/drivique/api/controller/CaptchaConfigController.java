package com.drivique.api.controller;

import com.drivique.api.service.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/captcha")
@Tag(name = "Captcha", description = "Configuración pública del captcha anti-bots")
public class CaptchaConfigController {

    private final CaptchaService captcha;

    public CaptchaConfigController(CaptchaService captcha) {
        this.captcha = captcha;
    }

    @GetMapping("/config")
    @Operation(summary = "Configuración del captcha", description = "Entrega la clave pública (site key) y si el captcha está activo. Nunca expone la clave secreta.")
    public Map<String, Object> config() {
        return Map.of("enabled", captcha.isEnabled(), "provider", "turnstile", "siteKey", captcha.getSiteKey());
    }
}
