package com.drivique.api.service;

import com.drivique.api.config.Auditable;
import com.drivique.api.dto.CashPaymentConfirmationRequestDTO;
import com.drivique.api.dto.CashPaymentConfirmationResponseDTO;
import com.drivique.api.dto.GenerateContractRequestDTO;
import com.drivique.api.dto.CashPendingReservationDTO;
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
import java.util.List;

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
    private BranchUserRepository branchUsers;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ContractStatusRepository contractStatuses;
    private ReservationService reservationService;

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

    @org.springframework.beans.factory.annotation.Autowired
    public void setListingDependencies(BranchUserRepository branchUsers, ReservationService reservationService) {
        this.branchUsers = branchUsers;
        this.reservationService = reservationService;
    }

    @Transactional(readOnly = true)
    public List<CashPendingReservationDTO> listForStaff(String email) {
        User staff = users.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        boolean superAdmin = staff.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getCode()));
        List<Reservation> found = superAdmin
                ? reservations.findByCashPaymentCodeIsNotNullOrderByCreatedAtDesc()
                : branchUsers.findByUserId(staff.getId())
                        .map(b -> reservations.findByCashPaymentBranchIdAndCashPaymentCodeIsNotNullOrderByCreatedAtDesc(b.getBranchId()))
                        .orElseGet(List::of);
        return found.stream().map(r -> new CashPendingReservationDTO(reservationService.toResponse(r),
                r.getCustomer().getPhone(), r.getCustomer().getDocumentNumber())).toList();
    }

    @Transactional
    @Auditable(domain = "BILLING", entity = "Payment", action = "INSERT", description = "Cobro en efectivo confirmado en sucursal", includeResult = false)
    public CashPaymentConfirmationResponseDTO confirm(CashPaymentConfirmationRequestDTO input, String email) {
        Reservation reservation = reservations.findByCashPaymentCodeIgnoreCase(input.cashPaymentCode().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Código de pago en efectivo no encontrado"));
        if (reservation.getStatus() != null && "PENDING_DOCUMENT_REVIEW".equalsIgnoreCase(reservation.getStatus().getCode()))
            throw new ConflictException("La reserva se encuentra en revisión de documentos y no puede ser pagada en efectivo hasta que los documentos sean aprobados.");
        if (reservation.getStatus() != null && "PENDING_SCHEDULE".equalsIgnoreCase(reservation.getStatus().getCode()))
            throw new ConflictException("La reserva se encuentra en espera de horario de atención de la sucursal.");
        if (reservation.getCashPaymentBranch() == null || !reservation.getCashPaymentBranch().allowsCashPayment())
            throw new ConflictException("La sede de la reserva no permite pagos en efectivo.");
        if (reservation.getCashPaymentExpiresAt() != null && reservation.getCashPaymentExpiresAt().isBefore(Instant.now()))
            throw new ConflictException("El código de pago en efectivo está vencido.");
        if (payments.existsByGatewayProviderAndGatewayReference("EFECTIVO_CAJA", reservation.getCashPaymentCode()))
            throw new ConflictException("El pago en efectivo ya fue confirmado.");

        User cashier = users.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("Cajero no encontrado"));

        RentalContract contract = contracts.findByReservationId(reservation.getId()).orElseGet(() -> {
            contractService.generateContract(new GenerateContractRequestDTO(reservation.getId()), email);
            return contracts.findByReservationId(reservation.getId())
                    .orElseThrow(() -> new IllegalStateException("No se pudo generar el contrato para la reserva."));
        });

        boolean contractSigned = false;
        if (contractStatuses != null && contract.getStatus() != null && "DRAFT".equalsIgnoreCase(contract.getStatus().getCode())) {
            contract.setStatus(contractStatuses.findByCodeIgnoreCase("PENDING_SIGNATURE")
                    .orElseThrow(() -> new ResourceNotFoundException("Estado PENDING_SIGNATURE no encontrado")));
        } else if (contract.getStatus() != null && "ACTIVE".equalsIgnoreCase(contract.getStatus().getCode())) {
            contractSigned = true;
        }

        String resStatusCode = contractSigned ? "CONFIRMED" : "PENDING_SIGNATURE";
        reservation.setStatus(reservationStatuses.findByCodeIgnoreCase(resStatusCode)
                .or(() -> reservationStatuses.findByCodeIgnoreCase("CONFIRMED"))
                .orElseThrow(() -> new ResourceNotFoundException("Estado de reserva no encontrado")));
        reservations.saveAndFlush(reservation);

        Payment payment = new Payment(contract,
                methods.findByCodeIgnoreCaseAndActiveTrue("CASH").orElseThrow(() -> new ResourceNotFoundException("Método CASH no encontrado")),
                paymentStatuses.findByCodeIgnoreCase("APPROVED").orElseThrow(() -> new ResourceNotFoundException("Estado APPROVED no encontrado")),
                "EFECTIVO_CAJA", reservation.getCashPaymentCode(),
                contract.getBaseAmount(),
                currencies.findByCodeIgnoreCaseAndActiveTrue("COP").orElseThrow(() -> new ResourceNotFoundException("Moneda COP no encontrada")), cashier);
        payment.setPaidAt(Instant.now());
        Payment saved = payments.saveAndFlush(payment);
        receipts.generateFor(saved);

        notificationService.send(reservation.getCustomer(), "EMAIL", "RESERVATION_PAYMENT_APPROVED",
                "Pago aprobado - contrato disponible",
                "El pago en efectivo de tu reserva " + reservation.getCode() + " fue aprobado. Ya puedes firmar el contrato; el PDF estará disponible después de la firma.",
                reservation.getId());

        try {
            notificationService.emitDomainEvent(
                    reservation.getCustomer(),
                    "PAYMENT",
                    "HIGH",
                    saved.getId(),
                    java.util.Map.of(
                            "bookingCode", reservation.getCode(),
                            "amount", saved.getAmount(),
                            "currency", "COP",
                            "paymentMethod", "EFECTIVO"
                    ),
                    "PAY-" + saved.getId()
            );
        } catch (Exception e) {
            // No bloquear confirmación de pago
        }

        return new CashPaymentConfirmationResponseDTO(saved.getId(), saved.getGatewayReference(), saved.getAmount(), "APPROVED", saved.getPaidAt());
    }
}
