package com.drivique.api.mapper;
import com.drivique.api.dto.SecurityConfigurationResponseDTO;
import com.drivique.api.model.SecurityConfiguration;
public final class SecurityConfigurationMapper {
    private SecurityConfigurationMapper() {}
    public static SecurityConfigurationResponseDTO response(SecurityConfiguration value) {
        return new SecurityConfigurationResponseDTO(value.getConfigKey(), value.getConfigValue());
    }
}
