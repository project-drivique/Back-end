package com.drivique.api.config;

import com.drivique.api.exception.ApiProblem;
import com.drivique.api.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Protocolo estándar Idempotency-Key para las operaciones que crean reservas o mueven dinero.
 *
 * El cliente envía un UUID único por intención ("Idempotency-Key"). La primera petición se ejecuta y su respuesta se
 * guarda (con vencimiento); si llega otra con la misma clave y el mismo contenido se devuelve la respuesta guardada
 * SIN volver a ejecutar la reserva ni el cobro. Con la misma clave y contenido distinto se rechaza (422).
 * Dos peticiones simultáneas con la misma clave: la segunda espera a la primera y recibe su misma respuesta.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{8,80}$");
    private static final Pattern PROTECTED_PATH = Pattern.compile("^/v1/(reservations|payments)(/.*)?$");
    private static final String WEBHOOK_PATH = "/v1/payments/wompi/webhook";
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final Duration MAX_WAIT = Duration.ofSeconds(10);

    private final IdempotencyService idempotency;
    private final ObjectMapper mapper = new ObjectMapper();

    /** El servicio es opcional: en pruebas de capa web (sin base de datos) el filtro simplemente deja pasar. */
    public IdempotencyFilter(ObjectProvider<IdempotencyService> idempotency) {
        this.idempotency = idempotency.getIfAvailable();
    }

    private static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = pathOf(request);
        // El webhook de la pasarela se protege con su firma, su estado y comprobante único; no lo envía una persona
        return !PROTECTED_PATH.matcher(path).matches() || WEBHOOK_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (idempotency == null) {
            chain.doFilter(request, response);
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (key == null || key.isBlank() || auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        key = key.trim();
        if (!KEY_FORMAT.matcher(key).matches()) {
            problem(request, response, HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID",
                    "La cabecera Idempotency-Key debe tener entre 8 y 80 caracteres (letras, números, guion o guion bajo); se recomienda un UUID.");
            return;
        }

        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            problem(request, response, HttpStatus.PAYLOAD_TOO_LARGE, "REQUEST_TOO_LARGE", "El cuerpo de la petición es demasiado grande.");
            return;
        }
        CachedBodyRequest cached = new CachedBodyRequest(request, body);
        String path = pathOf(request);
        String hash = sha256(request.getMethod() + "\n" + path + "\n" + request.getQueryString() + "\n", body);
        String user = auth.getName().toLowerCase();

        IdempotencyService.Claim claim = idempotency.claim(user, key, request.getMethod(), path, hash);
        if (claim instanceof IdempotencyService.Claim.Pending) {
            claim = waitForTheFirstRequest(user, key, claim);
        }

        if (claim instanceof IdempotencyService.Claim.Replay replay) {
            replay(response, key, replay);
        } else if (claim instanceof IdempotencyService.Claim.Conflict) {
            problem(request, response, HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                    "Esta Idempotency-Key ya se usó con una petición diferente. Genera una clave nueva para cada operación.");
        } else if (claim instanceof IdempotencyService.Claim.Started started) {
            execute(started, key, cached, response, chain);
        } else {
            problem(request, response, HttpStatus.CONFLICT, "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                    "Ya hay una petición con esta Idempotency-Key en proceso. Reintenta en unos segundos.");
        }
    }

    private IdempotencyService.Claim waitForTheFirstRequest(String user, String key, IdempotencyService.Claim fallback) {
        long deadline = System.nanoTime() + MAX_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<IdempotencyService.Claim.Replay> done = idempotency.findCompleted(user, key);
            if (done.isPresent()) {
                return done.get();
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return fallback;
            }
        }
        return fallback;
    }

    private void execute(IdempotencyService.Claim.Started started, String key, HttpServletRequest request,
                         HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
        } catch (ServletException | IOException | RuntimeException ex) {
            idempotency.abandon(started.id());
            throw ex;
        }
        int status = wrapper.getStatus();
        // Los errores del servidor (5xx) y de sesión/límite no se guardan: el reintento debe poder ejecutarse de verdad
        if (status >= 500 || status == 401 || status == 403 || status == 429) {
            idempotency.abandon(started.id());
        } else {
            String body = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            idempotency.complete(started.id(), status, wrapper.getContentType(), body);
        }
        wrapper.setHeader(HEADER, key);
        wrapper.copyBodyToResponse();
    }

    private void replay(HttpServletResponse response, String key, IdempotencyService.Claim.Replay replay) throws IOException {
        response.setStatus(replay.status());
        if (replay.contentType() != null) {
            response.setContentType(replay.contentType());
        }
        response.setHeader(HEADER, key);
        response.setHeader(REPLAYED_HEADER, "true");
        if (replay.body() != null && !replay.body().isEmpty()) {
            response.getOutputStream().write(replay.body().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void problem(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(mapper.writeValueAsString(ApiProblem.of(status, request.getRequestURI(), message,
                List.of(new ApiProblem.ValidationError(HEADER, code, message)))));
    }

    private static String sha256(String prefix, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(prefix.getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Permite leer el cuerpo ya consumido (para calcular el hash) y que el controlador lo lea de nuevo. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.UTF_8.name();
            return new BufferedReader(new InputStreamReader(getInputStream(), java.nio.charset.Charset.forName(encoding)));
        }
    }

    /** Solo para pruebas: genera una clave válida. */
    public static String newKey() {
        return UUID.randomUUID().toString();
    }
}
