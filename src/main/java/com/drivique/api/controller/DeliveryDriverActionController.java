package com.drivique.api.controller;

import com.drivique.api.model.Reservation;
import com.drivique.api.model.ReservationDeliveryPoint;
import com.drivique.api.model.User;
import com.drivique.api.model.Vehicle;
import com.drivique.api.repository.ReservationDeliveryPointRepository;
import com.drivique.api.service.BrandEmailTheme;
import com.drivique.api.service.DeliveryActionTokens;
import com.drivique.api.service.DeliveryLogisticsService;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Acciones del conductor desde su correo al gestionar un domicilio.
 * Interfaz web moderna, segura y responsive para confirmar entrega o reportar código incorrecto.
 */
@Hidden
@RestController
@RequestMapping("/v1/public/deliveries/action")
public class DeliveryDriverActionController {

    private final DeliveryActionTokens tokens;
    private final DeliveryLogisticsService logistics;
    private final ReservationDeliveryPointRepository deliveryPoints;
    private final BrandEmailTheme brandTheme;

    public DeliveryDriverActionController(DeliveryActionTokens tokens, DeliveryLogisticsService logistics) {
        this(tokens, logistics, null, null);
    }

    @Autowired
    public DeliveryDriverActionController(
            DeliveryActionTokens tokens,
            DeliveryLogisticsService logistics,
            @Autowired(required = false) ReservationDeliveryPointRepository deliveryPoints,
            @Autowired(required = false) BrandEmailTheme brandTheme) {
        this.tokens = tokens;
        this.logistics = logistics;
        this.deliveryPoints = deliveryPoints;
        this.brandTheme = brandTheme;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> preview(@RequestParam String token, jakarta.servlet.http.HttpServletRequest request) {
        Optional<DeliveryActionTokens.Action> action = tokens.parse(token);
        if (action.isEmpty()) {
            return errorPage(410, "Enlace no válido o expirado",
                    "Este enlace de confirmación ha caducado por motivos de seguridad o no es válido. Si la entrega sigue en curso, solicita al encargado de la sucursal que reenvíe la asignación.",
                    "expired");
        }

        DeliveryActionTokens.Action a = action.get();
        ReservationDeliveryPoint point = findPoint(a);

        // Si ya está completada, mostrar pantalla informativa directa
        if (point != null && "COMPLETED".equals(point.getDeliveryStatus())) {
            return completedNoticePage(point);
        }

        // Si la entrega está programada para una fecha futura, las acciones están bloqueadas hasta el día del servicio
        if (isBeforeEventDate(point, a.pointType())) {
            return lockedFutureDeliveryPage(point, a.pointType());
        }

        boolean isConfirm = "CONFIRM".equalsIgnoreCase(a.action());
        String actionUrl = request.getRequestURI();
        return renderConfirmationCard(isConfirm, token, actionUrl, point, a.pointType());
    }

    @PostMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> execute(@RequestParam(name = "token", required = false) String token) {
        Optional<DeliveryActionTokens.Action> action = tokens.parse(token);
        if (action.isEmpty()) {
            return errorPage(410, "Enlace no válido o expirado",
                    "Este enlace ha caducado o ya no está disponible. No se registraron cambios.",
                    "expired");
        }

        DeliveryActionTokens.Action a = action.get();
        ReservationDeliveryPoint point = findPoint(a);

        if (isBeforeEventDate(point, a.pointType())) {
            return errorPage(400, "Acción aún no disponible",
                    "Por motivos de seguridad y control operativo, las acciones de confirmación y reporte de código solo se habilitan el día programado del servicio.",
                    "scheduled");
        }

        try {
            if ("CONFIRM".equalsIgnoreCase(a.action())) {
                logistics.confirmByDriver(a.reservationId(), a.pointType());
                return successResultPage(point, a.pointType(), true);
            } else {
                logistics.rejectByDriver(a.reservationId(), a.pointType());
                return successResultPage(point, a.pointType(), false);
            }
        } catch (RuntimeException e) {
            String message = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : "La entrega ya fue procesada o no admite esta acción en este momento.";
            return errorPage(409, "No se pudo registrar", message, "conflict");
        }
    }

    private ReservationDeliveryPoint findPoint(DeliveryActionTokens.Action a) {
        if (deliveryPoints == null || a.reservationId() == null || a.pointType() == null) {
            return null;
        }
        try {
            return deliveryPoints.findDetailedByReservationIdAndPointType(a.reservationId(), a.pointType().toUpperCase())
                    .orElseGet(() -> deliveryPoints.findByReservationIdAndPointType(a.reservationId(), a.pointType().toUpperCase()).orElse(null));
        } catch (Exception e) {
            return null;
        }
    }

    private ResponseEntity<String> renderConfirmationCard(boolean isConfirm, String token, String actionUrl, ReservationDeliveryPoint point, String pointType) {
        String serviceLabel = "RETURN".equalsIgnoreCase(pointType) ? "Devolución a domicilio" : "Entrega a domicilio";
        String badgeText = isConfirm ? "Confirmación de entrega" : "Reporte de código no coincide";
        String badgeClass = isConfirm ? "badge-confirm" : "badge-reject";
        String title = isConfirm ? "¿El código PIN del cliente coincide?" : "¿El código PIN del cliente NO coincide?";
        String subtitle = isConfirm
                ? "Verifica que el PIN verbal que te dio el cliente coincida exactamente con el de tu correo de asignación."
                : "Indica que el cliente no proporcionó el PIN correcto o no coincide con el asignado.";

        String noticeBox = isConfirm
                ? """
                  <div class="notice-box notice-confirm">
                    <div class="notice-icon">
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#059669" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
                        <polyline points="9 12 11 14 15 10"></polyline>
                      </svg>
                    </div>
                    <div>
                      <strong>Acción inmediata:</strong> Al confirmar, la entrega quedará marcada como completada en tiempo real y se enviará la notificación automática al cliente y a la sucursal.
                    </div>
                  </div>
                  """
                : """
                  <div class="notice-box notice-reject">
                    <div class="notice-icon">
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#dc2626" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="12" r="10"></circle>
                        <line x1="12" y1="8" x2="12" y2="12"></line>
                        <line x1="12" y1="16" x2="12.01" y2="16"></line>
                      </svg>
                    </div>
                    <div>
                      <strong>Advertencia:</strong> La entrega seguirá en estado pendiente. Se generará una alerta prioritaria para que el encargado de sucursal contacte al cliente y resuelva el código.
                    </div>
                  </div>
                  """;

        String buttonClass = isConfirm ? "btn-confirm" : "btn-reject";
        String buttonText = isConfirm ? "Sí, confirmar entrega completada" : "Sí, reportar código incorrecto";
        String buttonIcon = isConfirm
                ? "<svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polyline points=\"20 6 9 17 4 12\"></polyline></svg>"
                : "<svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><line x1=\"18\" y1=\"6\" x2=\"6\" y2=\"18\"></line><line x1=\"6\" y1=\"6\" x2=\"18\" y2=\"18\"></line></svg>";

        String summaryHtml = buildReservationSummaryHtml(point, serviceLabel);

        String body = """
            <div class="status-badge-container">
              <span class="badge %s">%s</span>
            </div>
            <h2 class="card-title">%s</h2>
            <p class="card-subtitle">%s</p>
            %s
            %s
            <form method="post" action="%s" id="deliveryForm" onsubmit="handleFormSubmit(this)">
              <input type="hidden" name="token" value="%s">
              <button type="submit" id="submitBtn" class="btn %s">
                %s
                <span>%s</span>
              </button>
            </form>
            """.formatted(
                badgeClass, escape(badgeText),
                escape(title),
                escape(subtitle),
                summaryHtml,
                noticeBox,
                escape(actionUrl),
                escape(token),
                buttonClass,
                buttonIcon,
                escape(buttonText)
            );

        return renderFullPage(200, isConfirm ? "Confirmar entrega" : "Reportar código", body);
    }

    private ResponseEntity<String> successResultPage(ReservationDeliveryPoint point, String pointType, boolean isConfirm) {
        String serviceLabel = "RETURN".equalsIgnoreCase(pointType) ? "Devolución" : "Entrega";
        String title = isConfirm ? "¡" + serviceLabel + " confirmada con éxito!" : "Reporte registrado correctamente";
        String subtitle = isConfirm
                ? "El servicio ha quedado registrado como completado en el sistema de Drivique."
                : "Se notificó a la sucursal y al cliente para validar el PIN y coordinar la entrega.";

        String iconHtml = isConfirm
                ? """
                  <div class="result-icon-wrapper result-success">
                    <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#059669" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="20 6 9 17 4 12"></polyline>
                    </svg>
                  </div>
                  """
                : """
                  <div class="result-icon-wrapper result-warning">
                    <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#d97706" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="12" cy="12" r="10"></circle>
                      <line x1="12" y1="8" x2="12" y2="12"></line>
                      <line x1="12" y1="16" x2="12.01" y2="16"></line>
                    </svg>
                  </div>
                  """;

        String summaryHtml = buildReservationSummaryHtml(point, serviceLabel + " a domicilio");

        String infoBox = isConfirm
                ? """
                  <div class="notice-box notice-confirm" style="text-align:center;">
                    ✓ Se ha enviado el comprobante digital al cliente y el reporte operativo a la sucursal encargada. ¡Excelente trabajo!
                  </div>
                  """
                : """
                  <div class="notice-box notice-reject" style="text-align:center;">
                    ⚠ Permanece atento a las indicaciones del encargado de sucursal antes de mover el vehículo.
                  </div>
                  """;

        String body = """
            %s
            <h2 class="card-title text-center">%s</h2>
            <p class="card-subtitle text-center">%s</p>
            %s
            %s
            <div style="text-align: center; margin-top: 24px;">
              <span class="safe-close-hint">Ya puedes cerrar esta ventana con total tranquilidad.</span>
            </div>
            """.formatted(iconHtml, escape(title), escape(subtitle), summaryHtml, infoBox);

        return renderFullPage(200, title, body);
    }

    private ResponseEntity<String> completedNoticePage(ReservationDeliveryPoint point) {
        String summaryHtml = buildReservationSummaryHtml(point, "Servicio a domicilio");
        String body = """
            <div class="result-icon-wrapper result-info">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#2563eb" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
                <polyline points="22 4 12 14.01 9 11.01"></polyline>
              </svg>
            </div>
            <h2 class="card-title text-center">Entrega ya completada</h2>
            <p class="card-subtitle text-center">Este servicio ya fue confirmado previamente y se encuentra finalizado en el sistema.</p>
            %s
            <div style="text-align: center; margin-top: 24px;">
              <span class="safe-close-hint">No se requiere ninguna acción adicional. Puedes cerrar esta ventana.</span>
            </div>
            """.formatted(summaryHtml);

        return renderFullPage(200, "Entrega ya completada", body);
    }

    private boolean isBeforeEventDate(ReservationDeliveryPoint point, String pointType) {
        if (point == null) return false;
        Reservation res = point.getReservation();
        if (res == null) return false;
        java.time.Instant target = "RETURN".equalsIgnoreCase(pointType) ? res.getReturnDate() : res.getPickupDate();
        if (target == null) return false;
        ZoneId zone = ZoneId.of("America/Bogota");
        java.time.LocalDate eventDate = target.atZone(zone).toLocalDate();
        java.time.LocalDate today = java.time.LocalDate.now(zone);
        return today.isBefore(eventDate);
    }

    private ResponseEntity<String> lockedFutureDeliveryPage(ReservationDeliveryPoint point, String pointType) {
        String serviceLabel = "RETURN".equalsIgnoreCase(pointType) ? "Devolución" : "Entrega";
        Reservation res = point != null ? point.getReservation() : null;
        java.time.Instant target = res != null ? ("RETURN".equalsIgnoreCase(pointType) ? res.getReturnDate() : res.getPickupDate()) : null;
        ZoneId zone = ZoneId.of("America/Bogota");
        String scheduledDateStr = target != null
                ? DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(zone).format(target)
                : "el día programado";

        String badgeText = "Acción bloqueada · " + serviceLabel + " programada";
        String title = "Disponible el día de la " + serviceLabel.toLowerCase();
        String subtitle = "Esta entrega está programada para el " + scheduledDateStr + ". Por motivos de seguridad operativa y control de auditoría, las opciones de confirmación y reporte de código solo se habilitarán el día del servicio.";

        String noticeBox = """
            <div class="notice-box notice-neutral">
              <div class="notice-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#475569" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                </svg>
              </div>
              <div>
                <strong>Seguridad y precisión:</strong> El cliente debe suministrarte su PIN verbal de 6 caracteres únicamente en el momento exacto de entregarle el vehículo.
              </div>
            </div>
            """;

        String summaryHtml = buildReservationSummaryHtml(point, serviceLabel + " a domicilio");

        String body = """
            <div class="status-badge-container">
              <span class="badge badge-locked">%s</span>
            </div>
            <div class="result-icon-wrapper" style="background:#f1f5f9; border:2px solid #cbd5e1;">
              <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="#475569" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
              </svg>
            </div>
            <h2 class="card-title text-center">%s</h2>
            <p class="card-subtitle text-center">%s</p>
            %s
            %s
            <button type="button" disabled class="btn btn-locked">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
              </svg>
              <span>Se desbloquea el %s</span>
            </button>
            <div style="text-align: center; margin-top: 18px;">
              <span class="safe-close-hint">Guarda este enlace; podrás abrirlo y gestionarlo el día del servicio.</span>
            </div>
            """.formatted(escape(badgeText), escape(title), escape(subtitle), summaryHtml, noticeBox, escape(scheduledDateStr));

        return renderFullPage(200, "Entrega programada", body);
    }

    private ResponseEntity<String> errorPage(int status, String title, String message, String type) {
        String iconStroke = "conflict".equals(type) ? "#d97706" : "#dc2626";
        String iconBgClass = "conflict".equals(type) ? "result-warning" : "result-danger";

        String body = """
            <div class="result-icon-wrapper %s">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="%s" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="15" y1="9" x2="9" y2="15"></line>
                <line x1="9" y1="9" x2="15" y2="15"></line>
              </svg>
            </div>
            <h2 class="card-title text-center">%s</h2>
            <p class="card-subtitle text-center">%s</p>
            <div class="notice-box notice-neutral" style="margin-top:20px;">
              <strong>¿Necesitas ayuda?</strong> Si tienes dudas sobre el estado de esta asignación, comunícate directamente con la sucursal asignada o con el soporte de Drivique.
            </div>
            """.formatted(iconBgClass, iconStroke, escape(title), escape(message));

        return renderFullPage(status, title, body);
    }

    private String buildReservationSummaryHtml(ReservationDeliveryPoint point, String fallbackType) {
        if (point == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"summary-card\">");

        Reservation res = null;
        try {
            res = point.getReservation();
        } catch (Exception ignored) {}

        String code = res != null && res.getCode() != null ? res.getCode() : "—";
        String serviceType = "RETURN".equalsIgnoreCase(point.getPointType()) ? "Devolución a domicilio" : "Entrega a domicilio";

        sb.append("<div class=\"summary-top\">");
        sb.append("<span class=\"summary-label\">Reserva</span>");
        sb.append("<span class=\"code-chip\">").append(escape(code)).append("</span>");
        sb.append("</div>");

        sb.append("<div class=\"summary-grid\">");

        // Tipo de Servicio
        sb.append("<div class=\"summary-item\">");
        sb.append("<div class=\"item-icon\"><svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6\"/></svg></div>");
        sb.append("<div><div class=\"item-title\">Operación</div><div class=\"item-value\">").append(escape(serviceType)).append("</div></div>");
        sb.append("</div>");

        // Vehículo
        try {
            Vehicle v = res != null ? res.getVehicle() : null;
            if (v != null) {
                String brandName = v.getBrand() != null && v.getBrand().getName() != null ? v.getBrand().getName() + " " : "";
                String vehicleText = brandName + (v.getModel() != null ? v.getModel() : "");
                String plate = v.getPlate() != null ? v.getPlate() : "";
                sb.append("<div class=\"summary-item\">");
                sb.append("<div class=\"item-icon\"><svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"2\" y=\"4\" width=\"20\" height=\"16\" rx=\"2\"/><path d=\"M6 16h.01M18 16h.01\"/></svg></div>");
                sb.append("<div><div class=\"item-title\">Vehículo</div><div class=\"item-value\">")
                  .append(escape(vehicleText))
                  .append(" <span class=\"plate-chip\">").append(escape(plate)).append("</span></div></div>");
                sb.append("</div>");
            }
        } catch (Exception ignored) {}

        // Cliente
        try {
            User c = res != null ? res.getCustomer() : null;
            if (c != null && c.getFullName() != null) {
                String phone = c.getPhone() != null && !c.getPhone().isBlank() ? c.getPhone() : null;
                sb.append("<div class=\"summary-item\">");
                sb.append("<div class=\"item-icon\"><svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2\"/><circle cx=\"12\" cy=\"7\" r=\"4\"/></svg></div>");
                sb.append("<div><div class=\"item-title\">Cliente</div><div class=\"item-value\">")
                  .append(escape(c.getFullName()));
                if (phone != null) {
                    sb.append(" · <a href=\"tel:").append(escape(phone)).append("\" class=\"phone-link\">📞 Llamar (").append(escape(phone)).append(")</a>");
                }
                sb.append("</div></div></div>");
            }
        } catch (Exception ignored) {}

        // Dirección
        try {
            StringBuilder addressBuilder = new StringBuilder();
            if (point.getAddress() != null && !point.getAddress().isBlank()) {
                addressBuilder.append(point.getAddress());
            }
            if (point.getNeighborhood() != null && !point.getNeighborhood().isBlank()) {
                if (addressBuilder.length() > 0) addressBuilder.append(", ");
                addressBuilder.append(point.getNeighborhood());
            }
            if (point.getCity() != null && point.getCity().getName() != null) {
                if (addressBuilder.length() > 0) addressBuilder.append(" (").append(point.getCity().getName()).append(")");
            }
            if (addressBuilder.length() > 0) {
                sb.append("<div class=\"summary-item\">");
                sb.append("<div class=\"item-icon\"><svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z\"/><circle cx=\"12\" cy=\"10\" r=\"3\"/></svg></div>");
                sb.append("<div><div class=\"item-title\">Dirección</div><div class=\"item-value\">").append(escape(addressBuilder.toString())).append("</div></div>");
                sb.append("</div>");
            }
        } catch (Exception ignored) {}

        sb.append("</div>"); // summary-grid
        sb.append("</div>"); // summary-card
        return sb.toString();
    }

    private ResponseEntity<String> renderFullPage(int status, String pageTitle, String innerContent) {
        String html = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
              <title>%s · Drivique</title>
              <link rel="preconnect" href="https://fonts.googleapis.com">
              <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
              <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
              <style>
                :root {
                  --primary: #1e3a8a;
                  --primary-gradient: linear-gradient(135deg, #09132b 0%%, #172554 45%%, #1e40af 100%%);
                  --slate-900: #0f172a;
                  --slate-800: #1e293b;
                  --slate-700: #334155;
                  --slate-600: #475569;
                  --slate-500: #64748b;
                  --slate-200: #e2e8f0;
                  --slate-100: #f1f5f9;
                  --slate-50: #f8fafc;
                  --emerald-600: #059669;
                  --emerald-500: #10b981;
                  --rose-600: #dc2626;
                  --rose-500: #ef4444;
                  --amber-500: #f59e0b;
                }
                * {
                  box-sizing: border-box;
                  margin: 0;
                  padding: 0;
                }
                body {
                  font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                  background-color: #0b1329;
                  background-image:
                    radial-gradient(at 50%% 0%%, rgba(30, 58, 138, 0.45) 0px, transparent 65%%),
                    radial-gradient(at 100%% 100%%, rgba(16, 185, 129, 0.1) 0px, transparent 50%%);
                  color: var(--slate-800);
                  min-height: 100vh;
                  padding: 24px 16px;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  -webkit-font-smoothing: antialiased;
                }
                .portal-wrapper {
                  max-width: 490px;
                  width: 100%%;
                  background: #ffffff;
                  border-radius: 24px;
                  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(255, 255, 255, 0.08);
                  overflow: hidden;
                  animation: fadeIn 0.3s ease-out;
                }
                @keyframes fadeIn {
                  from { opacity: 0; transform: translateY(10px); }
                  to { opacity: 1; transform: translateY(0); }
                }
                .portal-header {
                  background: var(--primary-gradient);
                  padding: 28px 24px;
                  text-align: center;
                  color: #ffffff;
                  position: relative;
                  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
                }
                .logo-emblem {
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  width: 52px;
                  height: 52px;
                  border-radius: 16px;
                  background: rgba(255, 255, 255, 0.12);
                  backdrop-filter: blur(8px);
                  margin-bottom: 12px;
                  border: 1px solid rgba(255, 255, 255, 0.2);
                  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.2);
                }
                .brand-title {
                  font-size: 24px;
                  font-weight: 800;
                  letter-spacing: 2px;
                  color: #ffffff;
                  line-height: 1;
                }
                .brand-subtitle {
                  font-size: 11px;
                  font-weight: 600;
                  letter-spacing: 1.5px;
                  text-transform: uppercase;
                  color: #93c5fd;
                  margin-top: 6px;
                }
                .portal-body {
                  padding: 28px 24px 32px;
                }
                .status-badge-container {
                  display: flex;
                  justify-content: center;
                  margin-bottom: 16px;
                }
                .badge {
                  display: inline-flex;
                  align-items: center;
                  gap: 6px;
                  padding: 6px 14px;
                  border-radius: 9999px;
                  font-size: 12px;
                  font-weight: 700;
                  text-transform: uppercase;
                  letter-spacing: 0.5px;
                }
                .badge-confirm {
                  background: #ecfdf5;
                  color: #065f46;
                  border: 1px solid #a7f3d0;
                }
                .badge-reject {
                  background: #fef2f2;
                  color: #991b1b;
                  border: 1px solid #fecaca;
                }
                .badge-locked {
                  background: #f1f5f9;
                  color: #475569;
                  border: 1px solid #cbd5e1;
                }
                .card-title {
                  font-size: 21px;
                  font-weight: 800;
                  color: var(--slate-900);
                  line-height: 1.3;
                  margin-bottom: 8px;
                }
                .card-subtitle {
                  font-size: 14px;
                  color: var(--slate-600);
                  line-height: 1.5;
                  margin-bottom: 20px;
                }
                .text-center {
                  text-align: center;
                }
                .summary-card {
                  background: var(--slate-50);
                  border: 1px solid var(--slate-200);
                  border-radius: 16px;
                  padding: 16px 18px;
                  margin-bottom: 20px;
                }
                .summary-top {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  padding-bottom: 12px;
                  margin-bottom: 12px;
                  border-bottom: 1px dashed var(--slate-200);
                }
                .summary-label {
                  font-size: 12px;
                  font-weight: 600;
                  color: var(--slate-500);
                  text-transform: uppercase;
                  letter-spacing: 0.5px;
                }
                .code-chip {
                  background: #eff6ff;
                  color: #1e40af;
                  border: 1px solid #bfdbfe;
                  font-size: 13px;
                  font-weight: 700;
                  padding: 3px 10px;
                  border-radius: 8px;
                  letter-spacing: 0.5px;
                }
                .summary-grid {
                  display: flex;
                  flex-direction: column;
                  gap: 10px;
                }
                .summary-item {
                  display: flex;
                  align-items: flex-start;
                  gap: 12px;
                }
                .item-icon {
                  width: 28px;
                  height: 28px;
                  border-radius: 8px;
                  background: #ffffff;
                  border: 1px solid var(--slate-200);
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  color: var(--slate-600);
                  flex-shrink: 0;
                  margin-top: 1px;
                }
                .item-title {
                  font-size: 11px;
                  font-weight: 600;
                  color: var(--slate-500);
                  text-transform: uppercase;
                  letter-spacing: 0.3px;
                }
                .item-value {
                  font-size: 14px;
                  font-weight: 600;
                  color: var(--slate-800);
                  line-height: 1.4;
                }
                .plate-chip {
                  background: #fef08a;
                  color: #854d0e;
                  border: 1px solid #fde047;
                  font-size: 11px;
                  font-weight: 800;
                  padding: 1px 6px;
                  border-radius: 4px;
                  letter-spacing: 0.5px;
                  margin-left: 4px;
                }
                .phone-link {
                  color: #2563eb;
                  font-weight: 700;
                  text-decoration: none;
                }
                .phone-link:hover {
                  text-decoration: underline;
                }
                .notice-box {
                  display: flex;
                  align-items: flex-start;
                  gap: 12px;
                  border-radius: 14px;
                  padding: 14px 16px;
                  margin-bottom: 24px;
                  font-size: 13.5px;
                  line-height: 1.5;
                }
                .notice-confirm {
                  background: #f0fdf4;
                  border: 1px solid #bbf7d0;
                  color: #166534;
                }
                .notice-reject {
                  background: #fef2f2;
                  border: 1px solid #fecaca;
                  color: #991b1b;
                }
                .notice-neutral {
                  background: #f8fafc;
                  border: 1px solid var(--slate-200);
                  color: var(--slate-700);
                }
                .notice-icon {
                  flex-shrink: 0;
                  margin-top: 2px;
                }
                .btn {
                  width: 100%%;
                  border: none;
                  border-radius: 14px;
                  padding: 16px 20px;
                  font-size: 16px;
                  font-weight: 700;
                  font-family: inherit;
                  color: #ffffff;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  gap: 10px;
                  cursor: pointer;
                  transition: transform 0.15s ease, box-shadow 0.15s ease, filter 0.15s ease;
                  -webkit-tap-highlight-color: transparent;
                }
                .btn:active {
                  transform: scale(0.98);
                }
                .btn:disabled {
                  opacity: 0.75;
                  cursor: not-allowed;
                  transform: none;
                }
                .btn-confirm {
                  background: linear-gradient(135deg, #10b981 0%%, #059669 100%%);
                  box-shadow: 0 10px 20px -3px rgba(16, 185, 129, 0.4), 0 4px 6px -4px rgba(16, 185, 129, 0.2);
                }
                .btn-confirm:hover:not(:disabled) {
                  filter: brightness(1.05);
                  box-shadow: 0 14px 24px -3px rgba(16, 185, 129, 0.5);
                }
                .btn-reject {
                  background: linear-gradient(135deg, #ef4444 0%%, #dc2626 100%%);
                  box-shadow: 0 10px 20px -3px rgba(239, 68, 68, 0.4), 0 4px 6px -4px rgba(239, 68, 68, 0.2);
                }
                .btn-reject:hover:not(:disabled) {
                  filter: brightness(1.05);
                  box-shadow: 0 14px 24px -3px rgba(239, 68, 68, 0.5);
                }
                .btn-locked {
                  background: #94a3b8;
                  cursor: not-allowed;
                  box-shadow: none;
                }
                .btn-locked:hover {
                  filter: none;
                  box-shadow: none;
                }
                .result-icon-wrapper {
                  width: 72px;
                  height: 72px;
                  border-radius: 50%%;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  margin: 8px auto 20px;
                }
                .result-success {
                  background: #ecfdf5;
                  border: 2px solid #a7f3d0;
                  box-shadow: 0 8px 24px rgba(16, 185, 129, 0.2);
                }
                .result-warning {
                  background: #fffbeb;
                  border: 2px solid #fde68a;
                  box-shadow: 0 8px 24px rgba(245, 158, 11, 0.2);
                }
                .result-danger {
                  background: #fef2f2;
                  border: 2px solid #fecaca;
                  box-shadow: 0 8px 24px rgba(239, 68, 68, 0.2);
                }
                .result-info {
                  background: #eff6ff;
                  border: 2px solid #bfdbfe;
                  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.2);
                }
                .safe-close-hint {
                  display: inline-block;
                  font-size: 13px;
                  font-weight: 600;
                  color: var(--slate-500);
                  background: var(--slate-100);
                  padding: 8px 16px;
                  border-radius: 20px;
                }
                .portal-footer {
                  border-top: 1px solid var(--slate-200);
                  background: var(--slate-50);
                  padding: 16px 24px;
                  text-align: center;
                }
                .secure-note {
                  display: inline-flex;
                  align-items: center;
                  gap: 6px;
                  font-size: 11px;
                  font-weight: 600;
                  color: var(--slate-500);
                  margin-bottom: 4px;
                }
                .copyright {
                  font-size: 11px;
                  color: #94a3b8;
                }
              </style>
            </head>
            <body>
              <div class="portal-wrapper">
                <header class="portal-header">
                  <div class="logo-emblem">
                    <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#60a5fa" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2"/>
                      <circle cx="7" cy="17" r="2"/>
                      <path d="M9 17h6"/>
                      <circle cx="17" cy="17" r="2"/>
                    </svg>
                  </div>
                  <h1 class="brand-title">DRIVIQUE</h1>
                  <p class="brand-subtitle">Portal de Logística y Domicilios</p>
                </header>
                <main class="portal-body">
                  %s
                </main>
                <footer class="portal-footer">
                  <div class="secure-note">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                    <span>Operación protegida con token criptográfico seguro</span>
                  </div>
                  <div class="copyright">© 2026 Drivique Mobility. Todos los derechos reservados.</div>
                </footer>
              </div>
              <script>
                function handleFormSubmit(form) {
                  var btn = form.querySelector('#submitBtn');
                  if (btn) {
                    btn.disabled = true;
                    btn.innerHTML = '<span>Procesando solicitud...</span>';
                  }
                }
              </script>
            </body>
            </html>
            """.formatted(escape(pageTitle), innerContent);

        if (brandTheme != null) {
            html = brandTheme.apply(html);
        }

        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(html);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

