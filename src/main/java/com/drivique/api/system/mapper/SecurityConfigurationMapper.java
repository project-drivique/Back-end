package com.drivique.api.system.mapper;
import com.drivique.api.system.dto.SecurityConfigurationResponseDTO;
import com.drivique.api.system.entity.SecurityConfiguration;
public final class SecurityConfigurationMapper {
    private SecurityConfigurationMapper() {}
    public static SecurityConfigurationResponseDTO response(SecurityConfiguration value) {
        return new SecurityConfigurationResponseDTO(value.getConfigKey(), value.getConfigValue());
    }
}
