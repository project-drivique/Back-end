package com.drivique.api.service;

import com.drivique.api.dto.CashPaymentConfirmationRequestDTO;
import com.drivique.api.dto.CashPaymentConfirmationResponseDTO;
import com.drivique.api.dto.GenerateContractRequestDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.Payment;
import com.drivique.api.model.RentalContract;
import com.drivique.api.model.Reservation;
import com.drivique.api.model.User;
import com.drivique.api.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CashPaymentService {
    private final ReservationRepository reservations;
    private final RentalContractRepository contracts;
    private final PaymentRepository payments;
    private final PaymentMethodRepository methods;
    private final PaymentStatusRepository paymentStatuses;
    private final ReservationStatusRepository reservationStatuses;
    private final CurrencyRepository currencies;
    private final UserRepository users;
    private final PaymentReceiptService receipts;
    private final ContractService contractService;
    private final NotificationService notificationService;

    public CashPaymentService(ReservationRepository reservations, RentalContractRepository contracts,
            PaymentRepository payments, PaymentMethodRepository methods, PaymentStatusRepository paymentStatuses,
            ReservationStatusRepository reservationStatuses, CurrencyRepository currencies, UserRepository users,
            PaymentReceiptService receipts, ContractService contractService, NotificationService notificationService) {
        this.reservations = reservations;
        this.contracts = contracts;
        this.payments = payments;
        this.methods = methods;
        this.paymentStatuses = paymentStatuses;
        this.reservationStatuses = reservationStatuses;
        this.currencies = currencies;
        this.users = users;
        this.receipts = receipts;
        this.contractService = contractService;
        this.notificationService = notificationService;
    }

    @Transactional
    public CashPaymentConfirmationResponseDTO confirm(CashPaymentConfirmationRequestDTO input, String email) {
        Reservation reservation = reservations.findByCashPaymentCodeIgnoreCase(input.cashPaymentCode().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Código de pago en efectivo no encontrado"));
        if (reservation.getCashPaymentBranch() == null || !reservation.getCashPaymentBranch().allowsCashPayment())
            throw new ConflictException("La sede de la reserva no permite pagos en efectivo.");
        if (reservation.getCashPaymentExpiresAt() != null && reservation.getCashPaymentExpiresAt().isBefore(Instant.now()))
            throw new ConflictException("El código de pago en efectivo está vencido.");
        if (payments.existsByGatewayProviderAndGatewayReference("EFECTIVO_CAJA", reservation.getCashPaymentCode()))
            throw new ConflictException("El pago en efectivo ya fue confirmado.");

        User cashier = users.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("Cajero no encontrado"));
        reservation.setStatus(reservationStatuses.findByCodeIgnoreCase("CONFIRMED")
                .orElseThrow(() -> new ResourceNotFoundException("Estado CONFIRMED no encontrado")));
        reservations.saveAndFlush(reservation);

        RentalContract contract = contracts.findByReservationId(reservation.getId()).orElseGet(() -> {
            contractService.generateContract(new GenerateContractRequestDTO(reservation.getId()), email);
            return contracts.findByReservationId(reservation.getId())
                    .orElseThrow(() -> new IllegalStateException("No se pudo generar el contrato para la reserva."));
        });

        Payment payment = new Payment(contract,
                methods.findByCodeIgnoreCaseAndActiveTrue("CASH").orElseThrow(() -> new ResourceNotFoundException("Método CASH no encontrado")),
                paymentStatuses.findByCodeIgnoreCase("APPROVED").orElseThrow(() -> new ResourceNotFoundException("Estado APPROVED no encontrado")),
                "EFECTIVO_CAJA", reservation.getCashPaymentCode(),
                contract.getBaseAmount().add(contract.getSecurityDeposit()),
                currencies.findByCodeIgnoreCaseAndActiveTrue("COP").orElseThrow(() -> new ResourceNotFoundException("Moneda COP no encontrada")), cashier);
        payment.setPaidAt(Instant.now());
        Payment saved = payments.saveAndFlush(payment);
        receipts.generateFor(saved);

        notificationService.send(reservation.getCustomer(), "EMAIL", "RESERVATION_PAYMENT_APPROVED",
                "Pago aprobado - contrato disponible",
                "El pago de tu reserva " + reservation.getCode() + " fue aprobado. Ya puedes firmar el contrato; el PDF estará disponible después de la firma.",
                reservation.getId());
        return new CashPaymentConfirmationResponseDTO(saved.getId(), saved.getGatewayReference(), saved.getAmount(), "APPROVED", saved.getPaidAt());
    }
}
