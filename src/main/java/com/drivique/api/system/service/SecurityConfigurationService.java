package com.drivique.api.system.service;

import com.drivique.api.system.dto.SecurityConfigurationResponseDTO;
import com.drivique.api.system.mapper.SecurityConfigurationMapper;
import com.drivique.api.system.repository.SecurityConfigurationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityConfigurationService {
    // New database settings must never become public automatically.
    private static final List<String> PUBLIC_KEYS = List.of(
            "SESSION_IDLE_TIMEOUT_MINUTES", "PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES");
    private final SecurityConfigurationRepository security;
    public SecurityConfigurationService(SecurityConfigurationRepository security) { this.security = security; }
    @Transactional(readOnly = true)
    public List<SecurityConfigurationResponseDTO> publicSettings() {
        return security.findByConfigKeyInOrderByConfigKeyAsc(PUBLIC_KEYS).stream()
                .map(SecurityConfigurationMapper::response).toList();
    }
}
