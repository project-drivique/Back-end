package com.drivique.api.service;

import com.drivique.api.model.Reservation;
import com.drivique.api.model.ReservationDeliveryPoint;
import com.drivique.api.model.User;
import com.drivique.api.model.Vehicle;
import com.drivique.api.service.email.EmailLanguage;
import com.drivique.api.service.email.EmailTexts;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Renderizador de las páginas HTML del portal de logística y domicilios para conductores.
 */
@Component
public class DeliveryDriverPortalViewRenderer {

    private static final EmailTexts DEFAULT_TEXTS = new EmailTexts();
    private static final String TEMPLATE_PATH = "templates/delivery-driver-portal.html";

    /**
     * La política general de la API bloquea estilos y tipografía de esta vista HTML; se permite solo lo que utiliza.
     */
    private static final String PAGE_POLICY = "default-src 'self'; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
            + "font-src https://fonts.gstatic.com; "
            + "img-src 'self' data:; "
            + "script-src 'self' 'unsafe-inline'; "
            + "form-action 'self'; frame-ancestors 'none'; base-uri 'none'";

    private final BrandEmailTheme brandTheme;
    private final EmailTexts emailTexts;
    private final String baseTemplate;

    public DeliveryDriverPortalViewRenderer() {
        this(null, null);
    }

    @Autowired
    public DeliveryDriverPortalViewRenderer(
            @Autowired(required = false) BrandEmailTheme brandTheme,
            @Autowired(required = false) EmailTexts emailTexts) {
        this.brandTheme = brandTheme;
        this.emailTexts = emailTexts != null ? emailTexts : DEFAULT_TEXTS;
        this.baseTemplate = loadTemplate();
    }

    private static String loadTemplate() {
        try (InputStream is = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar la plantilla del portal de conductores: " + TEMPLATE_PATH, e);
        }
    }

    public String t(Locale locale, String key, String... pairs) {
        Locale target = locale != null ? locale : EmailLanguage.DEFAULT;
        return emailTexts.get(target, key, pairs);
    }

    private static String kind(String pointType) {
        return "RETURN".equalsIgnoreCase(pointType) ? "RETURN" : "DELIVERY";
    }

    public ResponseEntity<String> renderConfirmationCard(
            Locale locale,
            boolean isConfirm,
            String token,
            String actionUrl,
            ReservationDeliveryPoint point,
            String pointType) {
        String serviceLabel = t(locale, "RETURN".equalsIgnoreCase(pointType) ? "page.serviceReturn" : "page.serviceDelivery");
        String badgeText = t(locale, isConfirm ? "page.badgeConfirm" : "page.badgeReject");
        String badgeClass = isConfirm ? "badge-confirm" : "badge-reject";
        String title = t(locale, isConfirm ? "page.titleConfirm" : "page.titleReject");
        String subtitle = t(locale, isConfirm ? "page.subtitleConfirm" : "page.subtitleReject");

        String noticeBox = isConfirm
                ? """
                  <div class="notice-box notice-confirm">
                    <div class="notice-icon">
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#059669" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
                        <polyline points="9 12 11 14 15 10"></polyline>
                      </svg>
                    </div>
                    <div>{{NOTICE_CONFIRM}}</div>
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
                    <div>{{NOTICE_REJECT}}</div>
                  </div>
                  """;

        noticeBox = noticeBox
                .replace("{{NOTICE_CONFIRM}}", t(locale, "page.noticeConfirm"))
                .replace("{{NOTICE_REJECT}}", t(locale, "page.noticeReject"));

        String buttonClass = isConfirm ? "btn-confirm" : "btn-reject";
        String buttonText = t(locale, isConfirm ? "page.buttonConfirm" : "page.buttonReject");
        String buttonIcon = isConfirm
                ? "<svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polyline points=\"20 6 9 17 4 12\"></polyline></svg>"
                : "<svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><line x1=\"18\" y1=\"6\" x2=\"6\" y2=\"18\"></line><line x1=\"6\" y1=\"6\" x2=\"18\" y2=\"18\"></line></svg>";

        String summaryHtml = buildReservationSummaryHtml(locale, point, serviceLabel);

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

        return renderFullPage(locale, 200, t(locale, isConfirm ? "page.pageConfirm" : "page.pageReject"), body);
    }

    public ResponseEntity<String> successResultPage(
            Locale locale,
            ReservationDeliveryPoint point,
            String pointType,
            boolean isConfirm) {
        String title = isConfirm ? t(locale, "page.successTitle." + kind(pointType)) : t(locale, "page.reportTitle");
        String subtitle = t(locale, isConfirm ? "page.successSubtitle" : "page.reportSubtitle");

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

        String summaryHtml = buildReservationSummaryHtml(
                locale, point, t(locale, "RETURN".equalsIgnoreCase(pointType) ? "page.serviceReturn" : "page.serviceDelivery"));

        String infoBox = isConfirm
                ? "<div class=\"notice-box notice-confirm\" style=\"text-align:center;\">{{INFO_CONFIRM}}</div>"
                : "<div class=\"notice-box notice-reject\" style=\"text-align:center;\">{{INFO_REJECT}}</div>";

        infoBox = infoBox
                .replace("{{INFO_CONFIRM}}", escape(t(locale, "page.infoConfirm")))
                .replace("{{INFO_REJECT}}", escape(t(locale, "page.infoReject")));

        String body = """
            %s
            <h2 class="card-title text-center">%s</h2>
            <p class="card-subtitle text-center">%s</p>
            %s
            %s
            <div style="text-align: center; margin-top: 24px;">
              <span class="safe-close-hint">%s</span>
            </div>
            """.formatted(iconHtml, escape(title), escape(subtitle), summaryHtml, infoBox, escape(t(locale, "page.closeHint")));

        return renderFullPage(locale, 200, title, body);
    }

    public ResponseEntity<String> completedNoticePage(Locale locale, ReservationDeliveryPoint point) {
        String summaryHtml = buildReservationSummaryHtml(locale, point, t(locale, "page.serviceGeneric"));
        String body = """
            <div class="result-icon-wrapper result-info">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#2563eb" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
                <polyline points="22 4 12 14.01 9 11.01"></polyline>
              </svg>
            </div>
            <h2 class="card-title text-center">%s</h2>
            <p class="card-subtitle text-center">%s</p>
            %s
            <div style="text-align: center; margin-top: 24px;">
              <span class="safe-close-hint">%s</span>
            </div>
            """.formatted(
                escape(t(locale, "page.completedTitle")),
                escape(t(locale, "page.completedText")),
                summaryHtml,
                escape(t(locale, "page.completedHint")));

        return renderFullPage(locale, 200, t(locale, "page.completedTitle"), body);
    }

    public ResponseEntity<String> lockedFutureDeliveryPage(
            Locale locale,
            ReservationDeliveryPoint point,
            String pointType) {
        String kind = kind(pointType);
        Reservation res = point != null ? point.getReservation() : null;
        java.time.Instant target = res != null
                ? ("RETURN".equalsIgnoreCase(pointType) ? res.getReturnDate() : res.getPickupDate())
                : null;
        ZoneId zone = ZoneId.of("America/Bogota");
        String scheduledDateStr = target != null
                ? DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(zone).format(target)
                : t(locale, "page.scheduledDay");

        String badgeText = t(locale, "page.lockedBadge." + kind);
        String title = t(locale, "page.lockedTitle." + kind);
        String subtitle = t(locale, "page.lockedSubtitle", "date", scheduledDateStr);

        String noticeBox = """
            <div class="notice-box notice-neutral">
              <div class="notice-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#475569" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                </svg>
              </div>
              <div>{{LOCKED_NOTICE}}</div>
            </div>
            """.replace("{{LOCKED_NOTICE}}", t(locale, "page.lockedNotice"));

        String summaryHtml = buildReservationSummaryHtml(
                locale, point, t(locale, "RETURN".equals(kind) ? "page.serviceReturn" : "page.serviceDelivery"));

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
              <span>%s</span>
            </button>
            <div style="text-align: center; margin-top: 18px;">
              <span class="safe-close-hint">%s</span>
            </div>
            """.formatted(
                escape(badgeText),
                escape(title),
                escape(subtitle),
                summaryHtml,
                noticeBox,
                escape(t(locale, "page.lockedButton", "date", scheduledDateStr)),
                escape(t(locale, "page.lockedHint")));

        return renderFullPage(locale, 200, t(locale, "page.lockedPageTitle"), body);
    }

    public ResponseEntity<String> errorPage(Locale locale, int status, String title, String message, String type) {
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
              %s
            </div>
            """.formatted(iconBgClass, iconStroke, escape(title), escape(message), t(locale, "page.help"));

        return renderFullPage(locale, status, title, body);
    }

    private String buildReservationSummaryHtml(Locale locale, ReservationDeliveryPoint point, String fallbackType) {
        if (point == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"summary-card\">");

        Reservation res = null;
        try {
            res = point.getReservation();
        } catch (Exception ignored) {
            res = null;
        }

        String code = res != null && res.getCode() != null ? res.getCode() : "—";
        String serviceType = t(locale, "RETURN".equalsIgnoreCase(point.getPointType()) ? "page.serviceReturn" : "page.serviceDelivery");

        sb.append("<div class=\"summary-top\">");
        sb.append("<span class=\"summary-label\">").append(escape(t(locale, "page.summaryReservation"))).append("</span>");
        sb.append("<span class=\"code-chip\">").append(escape(code)).append("</span>");
        sb.append("</div>");

        sb.append("<div class=\"summary-grid\">");
        sb.append("<div class=\"summary-item\">");
        sb.append("<div class=\"item-icon\"><svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6\"/></svg></div>");
        sb.append("<div><div class=\"item-title\">").append(escape(t(locale, "page.summaryOperation"))).append("</div><div class=\"item-value\">").append(escape(serviceType)).append("</div></div>");
        sb.append("</div>");

        appendVehicleSummary(locale, sb, res);
        appendCustomerSummary(locale, sb, res);
        appendAddressSummary(locale, sb, point);

        sb.append("</div></div>");
        return sb.toString();
    }

    private void appendVehicleSummary(Locale locale, StringBuilder sb, Reservation res) {
        try {
            Vehicle v = res != null ? res.getVehicle() : null;
            if (v != null) {
                String brandName = v.getBrand() != null && v.getBrand().getName() != null ? v.getBrand().getName() + " " : "";
                String vehicleText = brandName + (v.getModel() != null ? v.getModel() : "");
                String plate = v.getPlate() != null ? v.getPlate() : "";
                sb.append("<div class=\"summary-item\">");
                sb.append("<div class=\"item-icon\"><svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"2\" y=\"4\" width=\"20\" height=\"16\" rx=\"2\"/><path d=\"M6 16h.01M18 16h.01\"/></svg></div>");
                sb.append("<div><div class=\"item-title\">").append(escape(t(locale, "page.summaryVehicle"))).append("</div><div class=\"item-value\">")
                        .append(escape(vehicleText))
                        .append(" <span class=\"plate-chip\">").append(escape(plate)).append("</span></div></div>");
                sb.append("</div>");
            }
        } catch (Exception ignored) {
            // Relaciones perezosas fuera de sesión pueden omitirse sin romper el resumen visual
        }
    }

    private void appendCustomerSummary(Locale locale, StringBuilder sb, Reservation res) {
        try {
            User c = res != null ? res.getCustomer() : null;
            if (c != null && c.getFullName() != null) {
                String phone = c.getPhone() != null && !c.getPhone().isBlank() ? c.getPhone() : null;
                sb.append("<div class=\"summary-item\">");
                sb.append("<div class=\"item-icon\"><svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2\"/><circle cx=\"12\" cy=\"7\" r=\"4\"/></svg></div>");
                sb.append("<div><div class=\"item-title\">").append(escape(t(locale, "page.summaryCustomer"))).append("</div><div class=\"item-value\">")
                        .append(escape(c.getFullName()));
                if (phone != null) {
                    sb.append(" · <a href=\"tel:").append(escape(phone)).append("\" class=\"phone-link\">📞 ")
                            .append(escape(t(locale, "page.summaryCall"))).append(" (").append(escape(phone)).append(")</a>");
                }
                sb.append("</div></div></div>");
            }
        } catch (Exception ignored) {
            // Si el cliente no está inicializado se omite del resumen
        }
    }

    private void appendAddressSummary(Locale locale, StringBuilder sb, ReservationDeliveryPoint point) {
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
                sb.append("<div><div class=\"item-title\">").append(escape(t(locale, "page.summaryAddress"))).append("</div><div class=\"item-value\">")
                        .append(escape(addressBuilder.toString())).append("</div></div>");
                sb.append("</div>");
            }
        } catch (Exception ignored) {
            // Si la ciudad no está inicializada se omite del resumen
        }
    }

    private ResponseEntity<String> renderFullPage(Locale locale, int status, String pageTitle, String innerContent) {
        Locale targetLocale = locale != null ? locale : EmailLanguage.DEFAULT;
        String html = baseTemplate
                .replace("{{TITLE}}", escape(pageTitle))
                .replace("{{BODY}}", innerContent)
                .replace("{{LANG}}", targetLocale.toLanguageTag())
                .replace("{{PORTAL}}", escape(t(targetLocale, "page.portal")))
                .replace("{{SECURE}}", escape(t(targetLocale, "page.secure")))
                .replace("{{RIGHTS}}", escape(t(targetLocale, "page.rights")))
                .replace("{{PROCESSING}}", t(targetLocale, "page.processing").replace("'", "\\'"));

        if (brandTheme != null) {
            html = brandTheme.apply(html);
        }

        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML)
                .header("Content-Security-Policy", PAGE_POLICY)
                .body(html);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

