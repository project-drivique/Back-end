package com.drivique.api.service;

import com.drivique.api.model.User;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.config.ConsentRequirementsProperties;
import com.drivique.api.dto.ConsentRequirementDTO;
import com.drivique.api.dto.ConsentResponseDTO;
import com.drivique.api.dto.ConsentStatusResponseDTO;
import com.drivique.api.dto.CreateConsentRequestDTO;
import com.drivique.api.model.UserConsent;
import com.drivique.api.repository.UserConsentRepository;
import java.net.InetAddress;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {
    private final UserRepository users;
    private final UserConsentRepository consents;
    private final ConsentRequirementsProperties requirements;
    public ConsentService(UserRepository users, UserConsentRepository consents, ConsentRequirementsProperties requirements) {
        this.users = users; this.consents = consents; this.requirements = requirements;
    }
    @Transactional
    public ConsentResponseDTO register(String email, CreateConsentRequestDTO request, InetAddress ipAddress) {
        User user = user(email);
        String type = request.consentType().strip();
        String version = request.documentVersion().strip();
        if (consents.existsByUserIdAndConsentTypeAndDocumentVersion(user.getId(), type, version)) {
            throw new ConflictException("Consent already registered");
        }
        try { return response(consents.saveAndFlush(new UserConsent(user, type, version, ipAddress))); }
        catch (DataIntegrityViolationException exception) { throw new ConflictException("Consent already registered"); }
    }
    @Transactional(readOnly = true)
    public List<ConsentResponseDTO> mine(String email) {
        return consents.findByUserIdOrderByAcceptedAtDesc(user(email).getId()).stream().map(ConsentService::response).toList();
    }
    @Transactional(readOnly = true)
    public ConsentStatusResponseDTO status(String email) {
        List<ConsentRequirementDTO> required = requirements.getRequiredConsents().stream()
                .filter(value -> nonBlank(value.getConsentType()) && nonBlank(value.getDocumentVersion()))
                .map(value -> new ConsentRequirementDTO(value.getConsentType().strip(), value.getDocumentVersion().strip()))
                .distinct().toList();
        if (required.isEmpty()) return new ConsentStatusResponseDTO(false, false, List.of());
        List<UserConsent> accepted = consents.findByUserIdOrderByAcceptedAtDesc(user(email).getId());
        List<ConsentRequirementDTO> pending = required.stream().filter(requirement -> accepted.stream().noneMatch(consent ->
                Objects.equals(consent.getConsentType(), requirement.consentType())
                        && Objects.equals(consent.getDocumentVersion(), requirement.documentVersion()))).toList();
        return new ConsentStatusResponseDTO(true, !pending.isEmpty(), pending);
    }
    private User user(String email) {
        return users.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
    private static boolean nonBlank(String value) { return value != null && !value.isBlank(); }
    private static ConsentResponseDTO response(UserConsent consent) {
        return new ConsentResponseDTO(consent.getId(), consent.getConsentType(), consent.getDocumentVersion(),
                consent.getAcceptedAt(), consent.getIpAddress().getHostAddress());
    }
}
