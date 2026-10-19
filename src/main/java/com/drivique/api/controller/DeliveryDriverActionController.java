package com.drivique.api.controller;

import com.drivique.api.service.DeliveryActionTokens;
import com.drivique.api.service.DeliveryLogisticsService;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Acciones del conductor desde su correo. El enlace del correo abre una página con un botón (así los escáneres de
 * correo que visitan los enlaces no confirman nada solos) y solo el botón ejecuta la acción.
 */
@Hidden
@RestController
@RequestMapping("/v1/public/deliveries/action")
public class DeliveryDriverActionController {

    private final DeliveryActionTokens tokens;
    private final DeliveryLogisticsService logistics;

    public DeliveryDriverActionController(DeliveryActionTokens tokens, DeliveryLogisticsService logistics) {
        this.tokens = tokens;
        this.logistics = logistics;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> preview(@RequestParam String token, jakarta.servlet.http.HttpServletRequest request) {
        Optional<DeliveryActionTokens.Action> action = tokens.parse(token);
        if (action.isEmpty()) {
            return page(410, "Enlace no válido", "Este enlace venció o no es válido. Pide al encargado que reenvíe la asignación.");
        }
        boolean confirm = "CONFIRM".equals(action.get().action());
        String title = confirm ? "¿El código del cliente coincide?" : "¿El código del cliente NO coincide?";
        String text = confirm
                ? "Al confirmar, la entrega queda completada y el cliente y la sucursal reciben el aviso."
                : "Al rechazar, la entrega sigue pendiente y se avisa a la sucursal y al cliente para que revisen el código.";
        String color = confirm ? "#16a34a" : "#dc2626";
        String button = confirm ? "Sí, confirmar entrega" : "Sí, rechazar el código";
        String actionUrl = request.getRequestURI();
        String body = "<p>" + text + "</p><form method=\"post\" action=\"" + escape(actionUrl) + "\"><input type=\"hidden\" name=\"token\" value=\"" + escape(token) + "\">"
                + "<button type=\"submit\" style=\"background:" + color + ";color:#fff;border:0;border-radius:10px;padding:14px 24px;font-weight:700;font-size:16px;cursor:pointer\">"
                + button + "</button></form>";
        return page(200, title, body, true);
    }

    @PostMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> execute(@RequestParam(name = "token", required = false) String token) {
        Optional<DeliveryActionTokens.Action> action = tokens.parse(token);
        if (action.isEmpty()) {
            return page(410, "Enlace no válido", "Este enlace venció o no es válido.");
        }
        DeliveryActionTokens.Action a = action.get();
        try {
            if ("CONFIRM".equals(a.action())) {
                logistics.confirmByDriver(a.reservationId(), a.pointType());
                return page(200, "Entrega confirmada", "Gracias. La entrega quedó completada y se avisó a la sucursal y al cliente.");
            }
            logistics.rejectByDriver(a.reservationId(), a.pointType());
            return page(200, "Código rechazado", "Avisamos a la sucursal y al cliente para que revisen el código. Espera indicaciones del encargado.");
        } catch (RuntimeException e) {
            return page(409, "No se pudo registrar", e.getMessage() != null ? e.getMessage() : "La entrega ya no admite esta acción.");
        }
    }

    private static ResponseEntity<String> page(int status, String title, String body) {
        return page(status, title, "<p>" + escape(body) + "</p>", false);
    }

    private static ResponseEntity<String> page(int status, String title, String body, boolean raw) {
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Drivique</title></head>"
                + "<body style=\"font-family:Arial,sans-serif;background:#f8fafc;margin:0;padding:24px\"><div style=\"max-width:480px;margin:8vh auto;background:#fff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;color:#334155\">"
                + "<h2 style=\"margin:0 0 12px;color:#0f172a\">" + escape(title) + "</h2>" + body + "</div></body></html>";
        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(html);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
