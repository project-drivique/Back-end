package com.drivique.api.service;

import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.BranchUser;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.BranchUserRepository;
import com.drivique.api.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Resuelve a qué sucursal está limitado un usuario del panel (vacío = todas las sucursales). */
@Component
public class StaffBranchScope {

    private final UserRepository users;
    private final BranchUserRepository branchUsers;

    public StaffBranchScope(UserRepository users, BranchUserRepository branchUsers) {
        this.users = users;
        this.branchUsers = branchUsers;
    }

    /** Variante basada en los roles del token: no consulta la BD cuando el usuario es administrador. */
    @Transactional(readOnly = true)
    public Optional<UUID> forAuthentication(org.springframework.security.core.Authentication authentication) {
        boolean unrestricted = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(a -> Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_AGENT", "fleet:manage", "FLEET_MANAGE").contains(a));
        return unrestricted ? Optional.empty() : forStaff(authentication.getName());
    }

    @Transactional(readOnly = true)
    public Optional<UUID> forStaff(String email) {
        User staff = users.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Set<String> codes = staff.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        if (codes.stream().anyMatch(c -> Set.of("ADMIN", "SUPER_ADMIN", "AGENT").contains(c))) {
            return Optional.empty();
        }
        UUID branchId = branchUsers.findByUserId(staff.getId()).map(BranchUser::getBranchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes una sucursal asignada."));
        return Optional.of(branchId);
    }
}
