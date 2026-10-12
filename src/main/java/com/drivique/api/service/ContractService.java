package com.drivique.api.service;

import com.drivique.api.dto.ContractClauseResponseDTO;
import com.drivique.api.dto.ContractResponseDTO;
import com.drivique.api.dto.GenerateContractRequestDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class ContractService {

    private final RentalContractRepository rentalContractRepository;
    private final ContractStatusRepository contractStatusRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final ClauseService clauseService;
    private final FileStorageService storage;

    public ContractService(
            RentalContractRepository rentalContractRepository,
            ContractStatusRepository contractStatusRepository,
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            BranchRepository branchRepository,
            ClauseService clauseService,
            FileStorageService storage
    ) {
        this.rentalContractRepository = rentalContractRepository;
        this.contractStatusRepository = contractStatusRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.clauseService = clauseService;
        this.storage = storage;
    }

    @Transactional
    public ContractResponseDTO generateContract(GenerateContractRequestDTO request, String userEmail) {
        Reservation reservation = reservationRepository.findById(request.reservationId())
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));

        validateAccess(reservation, user);

        if (reservation.getStatus() != null) {
            String statusCode = reservation.getStatus().getCode();
            if ("CANCELLED".equalsIgnoreCase(statusCode) || "CANCELLED_BY_TIMEOUT".equalsIgnoreCase(statusCode)) {
                throw new ConflictException("No es posible emitir un contrato para una reserva cancelada (Estado actual: " + statusCode + ").");
            }
        }

        if (rentalContractRepository.existsByReservationId(reservation.getId())) {
            throw new ConflictException("Ya existe un contrato legal emitido para la reserva con código: " + reservation.getCode());
        }

        Branch pickupBranch = resolvePickupBranch(reservation);
        Branch returnBranch = resolveReturnBranch(reservation, pickupBranch);

        String reservationStatus = reservation.getStatus() == null ? "" : reservation.getStatus().getCode();
        String initialContractStatus = List.of("CONFIRMED", "IN_PROGRESS", "COMPLETED")
                .contains(reservationStatus.toUpperCase()) ? "PENDING_SIGNATURE" : "DRAFT";
        ContractStatus draftStatus = contractStatusRepository.findByCodeIgnoreCase(initialContractStatus)
                .orElseThrow(() -> new IllegalStateException("Estado contractual no encontrado: " + initialContractStatus));

        List<ContractClause> activeClauses = clauseService.getOrSeedDefaultClauses();

        String contractNumber = generateUniqueContractNumber();

        BigDecimal securityDeposit = BigDecimal.ZERO;
        if (reservation.getVehicle() != null && reservation.getVehicle().getCategory() != null
                && reservation.getVehicle().getCategory().getSecurityDeposit() != null) {
            securityDeposit = reservation.getVehicle().getCategory().getSecurityDeposit();
        }

        RentalContract contract = new RentalContract(
                contractNumber,
                reservation,
                reservation.getCustomer(),
                reservation.getVehicle(),
                draftStatus,
                pickupBranch,
                returnBranch,
                reservation.getPickupDate(),
                reservation.getReturnDate(),
                reservation.getTotalEstimated(),
                securityDeposit,
                activeClauses
        );

        RentalContract saved = rentalContractRepository.save(contract);
        return mapToDTO(saved);
    }

    @Transactional(readOnly = true)
    public ContractResponseDTO getContractById(UUID id, String userEmail) {
        RentalContract contract = rentalContractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado"));

        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        validateAccess(contract.getReservation(), user);

        return mapToDTO(contract);
    }

    @Transactional(readOnly = true)
    public ContractResponseDTO getContractByReservationId(UUID reservationId, String userEmail) {
        RentalContract contract = rentalContractRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado para la reserva especificada"));

        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        validateAccess(contract.getReservation(), user);

        return mapToDTO(contract);
    }

    @Transactional(readOnly = true)
    public ContractResponseDTO getContractByNumber(String contractNumber, String userEmail) {
        RentalContract contract = rentalContractRepository.findByContractNumber(contractNumber.strip())
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado con número: " + contractNumber));

        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        validateAccess(contract.getReservation(), user);

        return mapToDTO(contract);
    }

    @Transactional
    public ContractResponseDTO getOrGenerateByReservationCode(String code, String userEmail) {
        Reservation reservation = reservationRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
        return rentalContractRepository.findByReservationId(reservation.getId())
                .map(this::mapToDTO)
                .orElseGet(() -> generateContract(new GenerateContractRequestDTO(reservation.getId()), userEmail));
    }

    @Transactional(readOnly = true)
    public byte[] downloadPdf(UUID id, String userEmail) {
        RentalContract contract = rentalContractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado"));
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        validateAccess(contract.getReservation(), user);
        if (contract.getPdfUrl() == null) throw new ConflictException("El contrato aún no está firmado.");
        return storage.read(contract.getPdfUrl());
    }

    private Branch resolvePickupBranch(Reservation reservation) {
        if (reservation.getDeliveryPoints() != null) {
            for (ReservationDeliveryPoint pt : reservation.getDeliveryPoints()) {
                if ("PICKUP".equalsIgnoreCase(pt.getPointType()) && pt.getBranch() != null) {
                    return pt.getBranch();
                }
            }
        }
        if (reservation.getCashPaymentBranch() != null) {
            return reservation.getCashPaymentBranch();
        }
        if (reservation.getVehicle() != null && reservation.getVehicle().getCurrentBranch() != null) {
            return reservation.getVehicle().getCurrentBranch();
        }
        return branchRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró sede física de entrega configurada"));
    }

    private Branch resolveReturnBranch(Reservation reservation, Branch fallbackPickup) {
        if (reservation.getDeliveryPoints() != null) {
            for (ReservationDeliveryPoint pt : reservation.getDeliveryPoints()) {
                if ("RETURN".equalsIgnoreCase(pt.getPointType()) && pt.getBranch() != null) {
                    return pt.getBranch();
                }
            }
        }
        return fallbackPickup;
    }

    private String generateUniqueContractNumber() {
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        String prefix = "CTR-" + year + "-";
        long count = rentalContractRepository.countByContractNumberStartingWith(prefix) + 1;
        String number = String.format("CTR-%d-%04d", year, count);
        int counter = (int) count;
        while (rentalContractRepository.findByContractNumber(number).isPresent()) {
            counter++;
            number = String.format("CTR-%d-%04d", year, counter);
        }
        return number;
    }

    private void validateAccess(Reservation reservation, User user) {
        boolean isOwner = reservation.getCustomer().getId().equals(user.getId());
        boolean isStaff = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> List.of("ADMIN", "SUPER_ADMIN", "AGENT", "EMPLOYEE", "BRANCH_ADMIN").contains(r.getCode()));

        if (!isOwner && !isStaff) {
            throw new ResourceNotFoundException("Reserva no encontrada");
        }
    }

    public ContractResponseDTO mapToDTO(RentalContract c) {
        List<ContractClauseResponseDTO> clauseDTOs = c.getClauses() != null
                ? c.getClauses().stream()
                .map(cl -> new ContractClauseResponseDTO(
                        cl.getId(),
                        cl.getVersion(),
                        cl.getSortOrder(),
                        cl.getTitle(),
                        cl.getContent(),
                        cl.isActive()
                ))
                .toList()
                : List.of();

        String customerFullName = c.getCustomer() != null
                ? c.getCustomer().getFirstName() + " " + c.getCustomer().getLastName()
                : "N/A";
        String customerEmail = c.getCustomer() != null ? c.getCustomer().getEmail() : "N/A";

        return new ContractResponseDTO(
                c.getId(),
                c.getContractNumber(),
                c.getReservation().getId(),
                c.getReservation().getCode(),
                c.getCustomer().getId(),
                customerFullName,
                customerEmail,
                c.getVehicle().getId(),
                c.getVehicle().getModel(),
                c.getVehicle().getPlate(),
                c.getStatus().getCode(),
                c.getStatus().getName(),
                c.getPickupBranch().getId(),
                c.getPickupBranch().getName(),
                c.getPickupBranch().getCity().getId(),
                c.getPickupBranch().getCity().getName(),
                c.getReturnBranch().getId(),
                c.getReturnBranch().getName(),
                c.getScheduledStartAt(),
                c.getScheduledEndAt(),
                c.getBaseAmount(),
                c.getSecurityDeposit(),
                c.getSignatureUrl(),
                c.getPdfUrl(),
                c.getSignedAt(),
                c.getDocumentVersion(),
                c.getAdditionalCharges(),
                c.getBaseAmount().add(c.getAdditionalCharges()),
                clauseDTOs,
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
