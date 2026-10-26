package com.drivique.api.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.drivique.api.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * Wompi puede enviar el mismo evento varias veces (reintentos). El webhook debe ser idempotente: el comprobante, el cambio
 * de estado y la notificación ocurren una sola vez, y un evento sin firma válida no cambia nada.
 */
class WompiWebhookIdempotencyTests {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentStatusRepository statuses = mock(PaymentStatusRepository.class);
    private final ReservationStatusRepository reservationStatuses = mock(ReservationStatusRepository.class);
    private final ContractStatusRepository contractStatuses = mock(ContractStatusRepository.class);
    private final WompiSignatureService signatures = mock(WompiSignatureService.class);
    private final PaymentReceiptService receipts = mock(PaymentReceiptService.class);
    private final NotificationService notifications = mock(NotificationService.class);

    private WompiPaymentService service;
    private Payment payment;
    private final AtomicReference<PaymentStatus> currentStatus = new AtomicReference<>();
    private Reservation reservation;

    private static PaymentStatus status(String code) {
        PaymentStatus s = mock(PaymentStatus.class);
        when(s.getCode()).thenReturn(code);
        return s;
    }

    @BeforeEach
    void setUp() {
        service = new WompiPaymentService(
                mock(RentalContractRepository.class), payments, mock(PaymentMethodRepository.class), statuses,
                mock(CurrencyRepository.class), mock(UserRepository.class), mock(UserSavedPaymentMethodRepository.class),
                reservationStatuses, contractStatuses, signatures, receipts, notifications,
                "pub", "integrity", "event", "https://checkout.wompi.co/p/", "https://sandbox.wompi.co/v1");

        PaymentStatus pending = status("PENDING");
        PaymentStatus approved = status("APPROVED");
        PaymentStatus declined = status("DECLINED");
        currentStatus.set(pending);
        when(statuses.findByCodeIgnoreCase("APPROVED")).thenReturn(Optional.of(approved));
        when(statuses.findByCodeIgnoreCase("DECLINED")).thenReturn(Optional.of(declined));

        User customer = mock(User.class);
        reservation = mock(Reservation.class);
        when(reservation.getCode()).thenReturn("RES-2026-0001");
        when(reservation.getId()).thenReturn(UUID.randomUUID());
        RentalContract contract = mock(RentalContract.class);
        when(contract.getReservation()).thenReturn(reservation);
        when(contract.getCustomer()).thenReturn(customer);
        when(reservationStatuses.findByCodeIgnoreCase(anyString())).thenReturn(Optional.of(mock(ReservationStatus.class)));
        when(contractStatuses.findByCodeIgnoreCase(anyString())).thenReturn(Optional.of(mock(ContractStatus.class)));

        payment = mock(Payment.class);
        when(payment.getId()).thenReturn(UUID.randomUUID());
        when(payment.getContract()).thenReturn(contract);
        when(payment.getAmount()).thenReturn(new java.math.BigDecimal("800000"));
        when(payment.getCurrency()).thenReturn(mock(Currency.class));
        when(payment.getStatus()).thenAnswer(inv -> currentStatus.get());
        doAnswer(inv -> {
            currentStatus.set(inv.getArgument(0));
            return null;
        }).when(payment).setStatus(any());
        when(payments.findByGatewayProviderAndGatewayReference("WOMPI", "PAY-1")).thenReturn(Optional.of(payment));
        when(signatures.validWebhook(any(), any(), anyString())).thenReturn(true);
    }

    private JsonNode event(String reference, String statusCode) throws Exception {
        return mapper.readTree("{\"data\":{\"transaction\":{\"reference\":\"" + reference + "\",\"status\":\"" + statusCode + "\"}}}");
    }

    @Test
    void theSameApprovedEventDeliveredThreeTimesGeneratesOneReceiptAndOneChange() throws Exception {
        JsonNode approved = event("PAY-1", "APPROVED");

        service.processWebhook(approved, "checksum");
        service.processWebhook(approved, "checksum");
        service.processWebhook(approved, "checksum");

        verify(receipts, times(1)).generateFor(payment);
        verify(payment, times(1)).setStatus(any());
        verify(payment, times(1)).setPaidAt(any());
        // La notificación lleva una clave determinista: aunque se intentara de nuevo, el servicio de alertas la descarta
        verify(notifications, times(1)).emitDomainEvent(any(), anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void eventWithAnInvalidSignatureChangesNothing() throws Exception {
        when(signatures.validWebhook(any(), any(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.processWebhook(event("PAY-1", "APPROVED"), "falso"))
                .isInstanceOf(AccessDeniedException.class);

        verify(payment, never()).setStatus(any());
        verify(receipts, never()).generateFor(any());
    }

    @Test
    void unknownReferenceIsRejectedWithoutSideEffects() throws Exception {
        assertThatThrownBy(() -> service.processWebhook(event("PAY-DESCONOCIDO", "APPROVED"), "checksum"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(receipts, never()).generateFor(any());
    }

    @Test
    void intermediateStatesAreIgnored() throws Exception {
        service.processWebhook(event("PAY-1", "PENDING"), "checksum");

        verify(payment, never()).setStatus(any());
        verify(receipts, never()).generateFor(any());
        assertThat(currentStatus.get().getCode()).isEqualTo("PENDING");
    }
}
