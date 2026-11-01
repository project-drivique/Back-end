package com.drivique.api.service;

import com.drivique.api.dto.*;
import com.drivique.api.exception.*;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class WompiPaymentService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(WompiPaymentService.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final RentalContractRepository contracts;
    private final PaymentRepository payments;
    private final PaymentMethodRepository methods;
    private final PaymentStatusRepository statuses;
    private final CurrencyRepository currencies;
    private final UserRepository users;
    private final UserSavedPaymentMethodRepository savedMethods;
    private final ReservationStatusRepository reservationStatuses;
    private final ContractStatusRepository contractStatuses;
    private final WompiSignatureService signatures;
    private final PaymentReceiptService receipts;
    private final NotificationService notificationService;
    private final String publicKey;
    private final String integritySecret;
    private final String eventSecret;
    private final String checkoutUrl;
    private final String apiUrl;
    private final RestTemplate restTemplate;

    @Autowired(required = false)
    private ReservationRepository reservations;

    @Autowired(required = false)
    private ContractService contractService;

    public WompiPaymentService(
            RentalContractRepository contracts,
            PaymentRepository payments,
            PaymentMethodRepository methods,
            PaymentStatusRepository statuses,
            CurrencyRepository currencies,
            UserRepository users,
            UserSavedPaymentMethodRepository savedMethods,
            ReservationStatusRepository reservationStatuses,
            ContractStatusRepository contractStatuses,
            WompiSignatureService signatures,
            PaymentReceiptService receipts,
            @Autowired(required = false) NotificationService notificationService,
            @Value("${payments.wompi.public-key:}") String publicKey,
            @Value("${payments.wompi.integrity-secret:}") String integritySecret,
            @Value("${payments.wompi.event-secret:}") String eventSecret,
            @Value("${payments.wompi.checkout-url:https://checkout.wompi.co/p/}") String checkoutUrl,
            @Value("${payments.wompi.api-url:https://sandbox.wompi.co/v1}") String apiUrl
    ) {
        this.contracts = contracts;
        this.payments = payments;
        this.methods = methods;
        this.statuses = statuses;
        this.currencies = currencies;
        this.users = users;
        this.savedMethods = savedMethods;
        this.reservationStatuses = reservationStatuses;
        this.contractStatuses = contractStatuses;
        this.signatures = signatures;
        this.receipts = receipts;
        this.notificationService = notificationService;
        this.publicKey = publicKey;
        this.integritySecret = integritySecret;
        this.eventSecret = eventSecret;
        this.checkoutUrl = checkoutUrl;
        this.apiUrl = apiUrl;
        this.restTemplate = new RestTemplate();
    }

    @Transactional
    public WompiCheckoutResponseDTO initiate(WompiInitiatePaymentRequestDTO input, String email) {
        RentalContract contract = resolveContractForInitiation(input, email);

        if (contract.getReservation() != null && contract.getReservation().getStatus() != null
                && "PENDING_DOCUMENT_REVIEW".equalsIgnoreCase(contract.getReservation().getStatus().getCode())) {
            throw new ConflictException("La reserva se encuentra en revisión de documentos y no puede ser pagada hasta que los documentos sean aprobados.");
        }
        if (contract.getReservation() != null && contract.getReservation().getStatus() != null
                && "PENDING_SCHEDULE".equalsIgnoreCase(contract.getReservation().getStatus().getCode())) {
            throw new ConflictException("La reserva se encuentra en espera de horario de atención de la sucursal.");
        }

        User user = currentUser(email);
        if (!contract.getCustomer().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Contrato no encontrado");
        }
        if (publicKey.isBlank()) {
            throw new IllegalStateException("No está configurada la llave pública de Wompi.");
        }

        String requestedMethod = input.paymentMethodCode() == null || input.paymentMethodCode().isBlank()
                ? "CREDIT_CARD"
                : input.paymentMethodCode().trim();
        String normalizedMethod = ("CARD".equalsIgnoreCase(requestedMethod) || "WOMPI".equalsIgnoreCase(requestedMethod))
                ? "CREDIT_CARD"
                : requestedMethod;

        PaymentMethod method = methods.findByCodeIgnoreCaseAndActiveTrue(normalizedMethod)
                .or(() -> methods.findByCodeIgnoreCaseAndActiveTrue("CREDIT_CARD"))
                .orElseThrow(() -> new ResourceNotFoundException("Método de pago no disponible"));
        PaymentStatus pending = statuses.findByCodeIgnoreCase("PENDING")
                .orElseThrow(() -> new ResourceNotFoundException("Estado PENDING no encontrado"));
        Currency currency = currencies.findByCodeIgnoreCaseAndActiveTrue("COP")
                .orElseThrow(() -> new ResourceNotFoundException("Moneda COP no encontrada"));

        String reference = "PAY-" + UUID.randomUUID().toString().replace("-", "");
        BigDecimal amount = contract.getBaseAmount();
        if ((amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
                && contract.getReservation() != null && contract.getReservation().getTotalEstimated() != null) {
            amount = contract.getReservation().getTotalEstimated();
        }
        payments.saveAndFlush(new Payment(contract, method, pending, reference, amount, currency));

        long cents = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new WompiCheckoutResponseDTO(checkoutUrl, publicKey, "COP", cents, reference,
                signatures.integrity(reference, cents, "COP", integritySecret));
    }

    private RentalContract resolveContractForInitiation(WompiInitiatePaymentRequestDTO input, String email) {
        if (input.contractId() != null) {
            Optional<RentalContract> byContractId = contracts.findById(input.contractId());
            if (byContractId.isPresent()) {
                return byContractId.get();
            }
            Optional<RentalContract> byReservationId = contracts.findByReservationId(input.contractId());
            if (byReservationId.isPresent()) {
                return byReservationId.get();
            }
            if (reservations != null && contractService != null) {
                Optional<Reservation> res = reservations.findById(input.contractId());
                if (res.isPresent()) {
                    contractService.generateContract(new GenerateContractRequestDTO(res.get().getId()), email);
                    return contracts.findByReservationId(res.get().getId())
                            .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado"));
                }
            }
        }
        if (input.reservationReference() != null && !input.reservationReference().isBlank()) {
            String rawRef = input.reservationReference().trim();
            String cleanRef = rawRef.contains("_") ? rawRef.substring(0, rawRef.indexOf('_')) : rawRef;
            if (reservations != null) {
                Reservation reservation = null;
                try {
                    UUID resUuid = UUID.fromString(cleanRef);
                    reservation = reservations.findById(resUuid).orElse(null);
                    if (reservation == null) {
                        Optional<RentalContract> existingById = contracts.findById(resUuid);
                        if (existingById.isPresent()) {
                            return existingById.get();
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // No es UUID, se busca por código de reserva
                }
                if (reservation == null) {
                    reservation = reservations.findByCodeIgnoreCase(cleanRef).orElse(null);
                }
                if (reservation != null) {
                    final UUID resId = reservation.getId();
                    return contracts.findByReservationId(resId).orElseGet(() -> {
                        if (contractService == null) {
                            throw new ResourceNotFoundException("Contrato no encontrado");
                        }
                        contractService.generateContract(new GenerateContractRequestDTO(resId), email);
                        return contracts.findByReservationId(resId)
                                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado"));
                    });
                }
            }
        }
        throw new ResourceNotFoundException("Contrato no encontrado");
    }

    /**
     * Consulta a la API de Wompi de manera fidedigna para verificar que la transacción
     * esté realmente APROBADA antes de confirmar la reserva, evitando la fabricación artificial de pagos.
     */
    @Transactional
    public Payment verifyAndRecordApprovedPayment(String transactionId, RentalContract contract, String email) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Identificador de transacción de Wompi obligatorio.");
        }

        String cleanTxId = transactionId.trim();
        String endpoint = (apiUrl.endsWith("/") ? apiUrl : apiUrl + "/") + "transactions/" + cleanTxId;

        JsonNode root;
        try {
            // Se lee como texto y se interpreta aparte: el cliente HTTP de Spring no sabe convertir esta clase de JsonNode
            String body = restTemplate.getForObject(endpoint, String.class);
            root = body == null || body.isBlank() ? null : MAPPER.readTree(body);
        } catch (Exception ex) {
            throw new ConflictException("No se pudo consultar la transacción con la pasarela Wompi: " + ex.getMessage());
        }

        if (root == null || !root.has("data")) {
            throw new ConflictException("Respuesta inválida recibida desde la pasarela Wompi.");
        }

        JsonNode data = root.path("data");
        String status = data.path("status").asText("").toUpperCase(Locale.ROOT);
        String reference = data.path("reference").asText("");
        long amountInCents = data.path("amount_in_cents").asLong(0);

        if (!"APPROVED".equals(status)) {
            throw new ConflictException("La transacción en Wompi no se encuentra aprobada (estado: " + status + ").");
        }
        if (!"COP".equalsIgnoreCase(data.path("currency").asText(""))) {
            throw new ConflictException("La transacción de Wompi no está en pesos colombianos (COP).");
        }

        Payment payment = payments.findByGatewayProviderAndGatewayReference("WOMPI", reference)
                .orElse(null);

        // La transacción debe pertenecer a ESTA reserva: no se acepta el pago aprobado de otra persona o de otra reserva.
        Reservation reservation = contract.getReservation();
        if (payment != null) {
            if (payment.getContract() == null || !payment.getContract().getId().equals(contract.getId())) {
                throw new ConflictException("La transacción de Wompi pertenece a otro contrato.");
            }
            if (payment.getAmount() != null && amountInCents != toCents(payment.getAmount())) {
                throw new ConflictException("El monto de la transacción no coincide con el pago registrado.");
            }
        } else {
            String base = reference.contains("_") ? reference.substring(0, reference.indexOf('_')) : reference;
            boolean sameReservation = reservation != null && !base.isBlank()
                    && (base.equalsIgnoreCase(String.valueOf(reservation.getId()))
                    || base.equalsIgnoreCase(reservation.getCode()));
            if (!sameReservation) {
                throw new ConflictException("La transacción de Wompi no corresponde a esta reserva.");
            }
            if (reservation.getTotalEstimated() == null || amountInCents < toCents(reservation.getTotalEstimated())) {
                throw new ConflictException("El monto pagado en Wompi no cubre el total de la reserva.");
            }
        }

        // Idempotencia: si este pago ya quedó aprobado no se vuelve a registrar ni a generar otro recibo.
        if (payment != null && payment.getStatus() != null && "APPROVED".equalsIgnoreCase(payment.getStatus().getCode())
                && payment.getPaidAt() != null) {
            return payment;
        }

        PaymentStatus approvedStatus = statuses.findByCodeIgnoreCase("APPROVED")
                .orElseThrow(() -> new ResourceNotFoundException("Estado de pago APPROVED no encontrado"));

        if (payment == null) {
            String methodType = data.path("payment_method_type").asText("CARD").toUpperCase(Locale.ROOT);
            PaymentMethod method = methods.findByCodeIgnoreCaseAndActiveTrue(methodType)
                    .or(() -> methods.findByCodeIgnoreCaseAndActiveTrue("CARD"))
                    .or(() -> methods.findAll().stream().findFirst())
                    .orElseThrow(() -> new ResourceNotFoundException("Método de pago no disponible"));

            Currency currency = currencies.findByCodeIgnoreCaseAndActiveTrue("COP")
                    .orElseThrow(() -> new ResourceNotFoundException("Moneda COP no encontrada"));

            BigDecimal amount = contract.getBaseAmount() != null
                    ? contract.getBaseAmount()
                    : BigDecimal.valueOf(amountInCents, 2);

            payment = new Payment(contract, method, approvedStatus, "WOMPI", reference, amount, currency, null);
        } else {
            payment.setStatus(approvedStatus);
        }

        payment.setPaidAt(Instant.now());
        payments.saveAndFlush(payment);

        receipts.generateFor(payment);
        return payment;
    }

    private static long toCents(BigDecimal value) {
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    @Transactional
    public void processWebhook(JsonNode event, String headerChecksum) {
        if (!signatures.validWebhook(event, headerChecksum, eventSecret)) {
            throw new AccessDeniedException("Firma de webhook inválida.");
        }

        JsonNode tx = event.path("data").path("transaction");
        String reference = tx.path("reference").asText();
        String statusCode = tx.path("status").asText().toUpperCase(Locale.ROOT);

        if (reference.isBlank() || !("APPROVED".equals(statusCode) || "DECLINED".equals(statusCode))) {
            return;
        }

        Payment payment = payments.findByGatewayProviderAndGatewayReference("WOMPI", reference)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado"));

        if (payment.getStatus().getCode().equalsIgnoreCase(statusCode)) {
            return;
        }

        payment.setStatus(statuses.findByCodeIgnoreCase(statusCode)
                .orElseThrow(() -> new ResourceNotFoundException("Estado de pago no encontrado")));

        if ("APPROVED".equals(statusCode)) {
            payment.setPaidAt(Instant.now());
            boolean contractSigned = payment.getContract() != null && payment.getContract().getStatus() != null
                    && "ACTIVE".equalsIgnoreCase(payment.getContract().getStatus().getCode());
            String resStatusTarget = contractSigned ? "CONFIRMED" : "PENDING_SIGNATURE";

            payment.getContract().getReservation().setStatus(
                    reservationStatuses.findByCodeIgnoreCase(resStatusTarget)
                            .or(() -> reservationStatuses.findByCodeIgnoreCase("CONFIRMED"))
                            .orElseThrow(() -> new ResourceNotFoundException("Estado de reserva no encontrado"))
            );

            payment.getContract().setStatus(
                    contractStatuses.findByCodeIgnoreCase(contractSigned ? "ACTIVE" : "PENDING_SIGNATURE")
                            .orElseThrow(() -> new ResourceNotFoundException("Estado PENDING_SIGNATURE no encontrado"))
            );

            receipts.generateFor(payment);
        }

        if (notificationService != null && payment.getContract() != null && payment.getContract().getCustomer() != null) {
            try {
                java.util.Map<String, Object> pMap = new java.util.HashMap<>();
                pMap.put("bookingCode", payment.getContract().getReservation().getCode());
                pMap.put("amount", payment.getAmount());
                pMap.put("currency", payment.getCurrency().getCode());
                pMap.put("status", statusCode);
                pMap.put("actionPath", "APPROVED".equals(statusCode) ? "/contrato/" + payment.getContract().getReservation().getId() : "/reservas");
                pMap.put("actionLabel", "APPROVED".equals(statusCode) ? "Continuar y Firmar mi Contrato" : "Reintentar Pago en Mis Reservas");
                notificationService.emitDomainEvent(payment.getContract().getCustomer(), "PAYMENT", "HIGH",
                        payment.getId(), pMap, "PAY-WOMPI-" + payment.getId() + "-" + statusCode);
            } catch (Exception ex) {
                LOG.warn("No se pudo emitir la notificación del evento de pago Wompi {}: {}", payment.getId(), ex.getMessage());
            }
        }
    }

    @Transactional
    public SavedPaymentMethodResponseDTO saveToken(SaveWompiPaymentMethodRequestDTO input, String email) {
        if (input.paymentToken().trim().matches("\\d{13,19}")) {
            throw new IllegalArgumentException("No se permite almacenar números de tarjeta.");
        }
        if (savedMethods.existsByPaymentToken(input.paymentToken().trim())) {
            throw new ConflictException("El método de pago ya está registrado.");
        }

        UserSavedPaymentMethod saved = savedMethods.saveAndFlush(new UserSavedPaymentMethod(
                currentUser(email),
                input.paymentToken().trim(),
                input.cardBrand().trim(),
                input.lastFour(),
                input.expMonth(),
                input.expYear()
        ));

        return new SavedPaymentMethodResponseDTO(saved.getId(), saved.getCardBrand(), saved.getLastFour(),
                saved.getExpMonth(), saved.getExpYear());
    }

    private User currentUser(String email) {
        return users.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }
}
