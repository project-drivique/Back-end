package com.drivique.api.service;

import com.drivique.api.config.Auditable;
import com.drivique.api.dto.AdminUserDTO;
import com.drivique.api.dto.AdminUserRequestDTO;
import com.drivique.api.dto.ForgotPasswordRequestDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.Branch;
import com.drivique.api.model.BranchUser;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.BranchUserRepository;
import com.drivique.api.repository.ReservationRepository;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.repository.UserRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administración de cuentas: listado, alta, edición de rol y sucursal, activación y baja. */
@Service
public class AdminUserService {

    private static final List<String> ACTIVE_RESERVATION_STATUSES = List.of("CONFIRMED", "IN_PROGRESS");
    private static final Set<String> STAFF_ROLES = Set.of("EMPLOYEE", "BRANCH_ADMIN");

    private final UserRepository users;
    private final RoleRepository roles;
    private final BranchRepository branches;
    private final BranchUserRepository assignments;
    private final ReservationRepository reservations;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public AdminUserService(UserRepository users, RoleRepository roles, BranchRepository branches,
                            BranchUserRepository assignments, ReservationRepository reservations,
                            PasswordEncoder passwordEncoder, AuthService authService) {
        this.users = users;
        this.roles = roles;
        this.branches = branches;
        this.assignments = assignments;
        this.reservations = reservations;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserDTO> list() {
        return users.findAll().stream()
                .filter(user -> user.getDeletedAt() == null)
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .map(this::toDto)
                .toList();
    }

    @Transactional
    @Auditable(domain = "USERS", entity = "User", action = "INSERT", description = "Creación de usuario")
    public AdminUserDTO create(AdminUserRequestDTO request) {
        String email = request.email().strip().toLowerCase();
        if (users.findByEmailIgnoreCaseAndDeletedAtIsNull(email).isPresent()) {
            throw new ConflictException("Ya existe un usuario con el correo " + email);
        }
        String[] names = splitName(request.fullName());
        User user = new User(names[0], names[1], email, passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setPhone(blankToNull(request.phone()));
        user.setDocumentNumber(blankToNull(request.documentNumber()));
        user.setEmailVerifiedAt(Instant.now());
        user.setAccountStatus("ACTIVE");
        Role role = role(request.roleCode());
        user.setRoles(new HashSet<>(Set.of(role)));
        User saved = users.saveAndFlush(user);
        assignBranch(saved, role, request.branchId());
        // La persona define su propia contraseña con el código que recibe por correo
        authService.forgotPassword(new ForgotPasswordRequestDTO(email));
        return toDto(saved);
    }

    @Transactional
    @Auditable(domain = "USERS", entity = "User", action = "UPDATE", description = "Actualización de usuario (datos, rol o sucursal)")
    public AdminUserDTO update(UUID id, AdminUserRequestDTO request, String actorEmail) {
        User user = find(id);
        String email = request.email().strip().toLowerCase();
        users.findByEmailIgnoreCaseAndDeletedAtIsNull(email).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("Ya existe un usuario con el correo " + email);
        });
        Role role = role(request.roleCode());
        boolean roleChanges = user.getRoles().stream().noneMatch(current -> current.getCode().equals(role.getCode()));
        if (roleChanges && user.getEmail().equalsIgnoreCase(actorEmail)) {
            throw new ConflictException("No puedes cambiar tu propio rol");
        }
        String[] names = splitName(request.fullName());
        user.setFirstName(names[0]);
        user.setLastName(names[1]);
        user.setEmail(email);
        user.setPhone(blankToNull(request.phone()));
        user.setDocumentNumber(blankToNull(request.documentNumber()));
        user.setRoles(new HashSet<>(Set.of(role)));
        user.setUpdatedAt(Instant.now());
        User saved = users.saveAndFlush(user);
        assignments.deleteByUserId(saved.getId());
        assignments.flush();
        assignBranch(saved, role, request.branchId());
        return toDto(saved);
    }

    @Transactional
    @Auditable(domain = "USERS", entity = "User", action = "UPDATE", description = "Activación o desactivación de cuenta")
    public AdminUserDTO toggleStatus(UUID id, String actorEmail) {
        User user = find(id);
        if (user.getEmail().equalsIgnoreCase(actorEmail)) {
            throw new ConflictException("No puedes desactivar tu propia cuenta");
        }
        user.setAccountStatus("ACTIVE".equals(user.getAccountStatus()) ? "INACTIVE" : "ACTIVE");
        user.setUpdatedAt(Instant.now());
        return toDto(users.saveAndFlush(user));
    }

    @Transactional
    @Auditable(domain = "USERS", entity = "User", action = "DELETE", description = "Eliminación lógica de usuario")
    public void delete(UUID id, String actorEmail) {
        User user = find(id);
        if (user.getEmail().equalsIgnoreCase(actorEmail)) {
            throw new ConflictException("No puedes eliminar tu propia cuenta");
        }
        if (activeReservations(user) > 0) {
            throw new ConflictException("No se puede eliminar el usuario porque tiene reservas activas (en curso o confirmadas).");
        }
        user.setDeletedAt(Instant.now());
        user.setAccountStatus("INACTIVE");
        user.setUpdatedAt(Instant.now());
        users.saveAndFlush(user);
        assignments.deleteByUserId(user.getId());
    }

    private void assignBranch(User user, Role role, UUID branchId) {
        if (!STAFF_ROLES.contains(role.getCode())) {
            return;
        }
        if (branchId == null) {
            throw new IllegalArgumentException("Los encargados deben tener una sucursal asignada");
        }
        Branch branch = branches.findByIdAndActiveTrue(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada"));
        assignments.saveAndFlush(new BranchUser(user, branch));
    }

    private Role role(String code) {
        return roles.findByCodeAndActiveTrue(code).orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado: " + code));
    }

    private User find(UUID id) {
        return users.findById(id).filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private long activeReservations(User user) {
        return reservations.countByCustomerIdAndStatusCodeIn(user.getId(), ACTIVE_RESERVATION_STATUSES);
    }

    private static String[] splitName(String fullName) {
        String clean = fullName.strip().replaceAll("\\s+", " ");
        int space = clean.indexOf(' ');
        if (space < 0) {
            throw new IllegalArgumentException("Ingresa nombre y apellido");
        }
        return new String[] {clean.substring(0, space), clean.substring(space + 1)};
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private AdminUserDTO toDto(User user) {
        Role role = user.getRoles().stream().min(Comparator.comparing(Role::getCode)).orElse(null);
        BranchUser assignment = assignments.findByUserId(user.getId()).orElse(null);
        Branch branch = assignment == null ? null : assignment.getBranch();
        return new AdminUserDTO(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getDocumentNumber(),
                role == null ? null : role.getCode(), role == null ? null : role.getName(), user.getAccountStatus(),
                "ACTIVE".equals(user.getAccountStatus()), branch == null ? null : branch.getId(),
                branch == null ? null : branch.getName(), activeReservations(user), user.getCreatedAt());
    }
}
