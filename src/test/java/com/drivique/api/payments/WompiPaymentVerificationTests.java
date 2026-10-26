package com.drivique.api.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.drivique.api.exception.ConflictException;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.drivique.api.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

class WompiPaymentVerificationTests {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentStatusRepository statuses = mock(PaymentStatusRepository.class);
    private final PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
    private final CurrencyRepository currencies = mock(CurrencyRepository.class);
    private final PaymentReceiptService receipts = mock(PaymentReceiptService.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);

    private WompiPaymentService service;
    private RentalContract contract;
    private Reservation reservation;
    private final UUID reservationId = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WompiPaymentService(
                mock(RentalContractRepository.class), payments, methods, statuses, currencies,
                mock(UserRepository.class), mock(UserSavedPaymentMethodRepository.class),
                mock(ReservationStatusRepository.class), mock(ContractStatusRepository.class),
                mock(WompiSignatureService.class), receipts, null,
                "pub", "integrity", "event", "https://checkout.wompi.co/p/", "https://sandbox.wompi.co/v1");
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);

        reservation = mock(Reservation.class);
        when(reservation.getId()).thenReturn(reservationId);
        when(reservation.getCode()).thenReturn("HLD-2026-0001");
        when(reservation.getTotalEstimated()).thenReturn(new BigDecimal("800000"));

        contract = mock(RentalContract.class);
        when(contract.getId()).thenReturn(contractId);
        when(contract.getReservation()).thenReturn(reservation);
        when(contract.getBaseAmount()).thenReturn(new BigDecimal("800000"));

        PaymentStatus approved = approvedStatus();
        when(statuses.findByCodeIgnoreCase("APPROVED")).thenReturn(Optional.of(approved));
        when(methods.findByCodeIgnoreCaseAndActiveTrue(anyString())).thenReturn(Optional.of(mock(PaymentMethod.class)));
        when(currencies.findByCodeIgnoreCaseAndActiveTrue("COP")).thenReturn(Optional.of(mock(Currency.class)));
        when(payments.findByGatewayProviderAndGatewayReference(anyString(), anyString())).thenReturn(Optional.empty());
    }

    private static PaymentStatus approvedStatus() {
        PaymentStatus status = mock(PaymentStatus.class);
        when(status.getCode()).thenReturn("APPROVED");
        return status;
    }

    private void wompiReturns(String status, String reference, long cents, String currency) throws Exception {
        JsonNode body = mapper.readTree("{\"data\":{\"status\":\"" + status + "\",\"reference\":\"" + reference
                + "\",\"amount_in_cents\":" + cents + ",\"currency\":\"" + currency + "\",\"payment_method_type\":\"CARD\"}}");
        when(restTemplate.getForObject(anyString(), org.mockito.ArgumentMatchers.eq(JsonNode.class))).thenReturn(body);
    }

    @Test
    void approvedTransactionOfThisReservation_IsRecorded() throws Exception {
        wompiReturns("APPROVED", "HLD-2026-0001_TX1730000000", 80000000, "COP");

        Payment payment = service.verifyAndRecordApprovedPayment("tx-1", contract, "a@b.com");

        assertThat(payment.getPaidAt()).isNotNull();
        verify(payments).saveAndFlush(any(Payment.class));
        verify(receipts).generateFor(any(Payment.class));
    }

    @Test
    void reservationIdAsReference_IsAlsoAccepted() throws Exception {
        wompiReturns("APPROVED", reservationId + "_1730000000", 80000000, "COP");

        assertThat(service.verifyAndRecordApprovedPayment("tx-1", contract, "a@b.com")).isNotNull();
    }

    @Test
    void declinedTransaction_IsRefused() throws Exception {
        wompiReturns("DECLINED", "HLD-2026-0001_TX1", 80000000, "COP");

        assertThatThrownBy(() -> service.verifyAndRecordApprovedPayment("tx-1", contract, "a@b.com"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no se encuentra aprobada");
        verify(payments, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void approvedTransactionOfAnotherReservation_IsRefused() throws Exception {
        wompiReturns("APPROVED", "HLD-OTRA-RESERVA_TX1", 80000000, "COP");

        assertThatThrownBy(() -> service.verifyAndRecordApprovedPayment("tx-1", contract, "a@b.com"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no corresponde a esta reserva");
        verify(payments, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void amountLowerThanReservationTotal_IsRefused() throws Exception {
        wompiReturns("APPROVED", "HLD-2026-0001_TX1", 1000, "COP");

        assertThatThrownBy(() -> service.verifyAndRecordApprovedPayment("tx-1", contract, "a@b.com"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no cubre el total");
    }

    @Test
    void foreignCurrency_IsRefused() throws Exception {
        wompiReturns("APPROVED", "HLD-2026-0001_TX1", 80000000, "USD");

        assertThatThrownBy(() -> service.verifyAndRecordApprovedPayment("tx-1", contract, "a@b.com"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("COP");
    }

    @Test
    void paymentOfAnotherContract_IsRefused() throws Exception {
        wompiReturns("APPROVED", "PAY-abc", 80000000, "COP");
        RentalContract other = mock(RentalContract.class);
        when(other.getId()).thenReturn(UUID.randomUUID());
        Payment foreign = mock(Payment.class);
        when(foreign.getContract()).thenReturn(other);
        when(payments.findByGatewayProviderAndGatewayReference("WOMPI", "PAY-abc")).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.verifyAndRecordApprovedPayment("tx-1", contract, "a@b.com"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("otro contrato");
    }

    @Test
    void repeatedConfirmation_DoesNotGenerateASecondReceipt() throws Exception {
        wompiReturns("APPROVED", "PAY-abc", 80000000, "COP");
        Payment already = mock(Payment.class);
        when(already.getContract()).thenReturn(contract);
        when(already.getAmount()).thenReturn(new BigDecimal("800000"));
        PaymentStatus approved = approvedStatus();
        when(already.getStatus()).thenReturn(approved);
        when(already.getPaidAt()).thenReturn(Instant.now());
        when(payments.findByGatewayProviderAndGatewayReference("WOMPI", "PAY-abc")).thenReturn(Optional.of(already));

        assertThat(service.verifyAndRecordApprovedPayment("tx-1", contract, "a@b.com")).isSameAs(already);
        verify(receipts, never()).generateFor(any(Payment.class));
    }
}
