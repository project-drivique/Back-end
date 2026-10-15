package com.drivique.api.service;

import com.drivique.api.config.Auditable;
import com.drivique.api.dto.*;
import com.drivique.api.exception.*;
import com.drivique.api.mapper.BranchMapper;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BranchService {
    private final BranchRepository branches; private final CityRepository cities;
    private final VehicleRepository vehicles; private final UserRepository users;
    public BranchService(BranchRepository branches, CityRepository cities, VehicleRepository vehicles, UserRepository users) {
        this.branches = branches; this.cities = cities; this.vehicles = vehicles; this.users = users;
    }

    @Transactional(readOnly = true)
    public List<BranchResponseDTO> all() { return branches.findAllByOrderByNameAsc().stream().map(b -> BranchMapper.branch(b, vehicles.countByCurrentBranchId(b.getId()))).toList(); }

    @Transactional(readOnly = true)
    public List<BranchStaffResponseDTO> managers() {
        return users.findActiveByRoleCode("BRANCH_ADMIN").stream()
                .map(u -> new BranchStaffResponseDTO(u.getId(), u.getFullName(), u.getEmail(), List.of("BRANCH_ADMIN"), null)).toList();
    }

    @Transactional
    @Auditable(domain = "LOCATION", entity = "Branch", action = "UPDATE", description = "Desactivación de sucursal", includeResult = false)
    public BranchResponseDTO deactivate(UUID id) {
        Branch branch = branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        if (vehicles.existsByCurrentBranchId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar la sucursal porque tiene vehículos asociados. Reasígnalos primero.");
        }
        if (branch.isActive()) branch.toggleStatus();
        return BranchMapper.branch(branches.saveAndFlush(branch));
    }

    @Transactional(readOnly = true) public List<BranchResponseDTO> active() { return branches.findByActiveTrueOrderByNameAsc().stream().map(BranchMapper::branch).toList(); }
    @Transactional(readOnly = true) public List<BranchResponseDTO> byCity(UUID cityId) {
        activeCity(cityId); return branches.findByCityIdAndActiveTrueOrderByNameAsc(cityId).stream().map(BranchMapper::branch).toList();
    }
    @Transactional(readOnly = true) public BranchResponseDTO detail(UUID id) {
        return BranchMapper.branch(branches.findByIdAndActiveTrue(id).orElseThrow(() -> new ResourceNotFoundException("Active branch not found")));
    }
    @Transactional public BranchResponseDTO create(BranchRequestDTO input) {
        validateHours(input); String name = input.name().strip();
        if (branches.existsByNameIgnoreCase(name)) throw new ConflictException("Branch name already exists");
        Branch created = new Branch(name, input.address().strip(), activeCity(input.cityId()), input.phone().strip(), input.openingTime(), input.closingTime(), input.allowsCashPayment());
        if (input.vehicleCapacity() != null) created.setVehicleCapacity(input.vehicleCapacity());
        try { return BranchMapper.branch(branches.saveAndFlush(created)); }
        catch (DataIntegrityViolationException exception) { throw new ConflictException("Branch conflicts with database constraints"); }
    }
    @Transactional public BranchResponseDTO update(UUID id, BranchRequestDTO input) {
        validateHours(input); Branch branch = branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        String name = input.name().strip(); if (branches.existsByNameIgnoreCaseAndIdNot(name, id)) throw new ConflictException("Branch name already exists");
        branch.update(name, input.address().strip(), activeCity(input.cityId()), input.phone().strip(), input.openingTime(), input.closingTime(), input.allowsCashPayment());
        if (input.vehicleCapacity() != null) branch.setVehicleCapacity(input.vehicleCapacity());
        try { return BranchMapper.branch(branches.saveAndFlush(branch)); } catch (DataIntegrityViolationException exception) { throw new ConflictException("Branch conflicts with database constraints"); }
    }
    @Transactional public BranchResponseDTO toggle(UUID id) {
        Branch branch = branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Branch not found")); branch.toggleStatus(); return BranchMapper.branch(branch);
    }
    private City activeCity(UUID id) { return cities.findById(id).filter(City::isActive).orElseThrow(() -> new ResourceNotFoundException("Active city not found")); }
    private void validateHours(BranchRequestDTO input) { if (!input.closingTime().isAfter(input.openingTime())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "closingTime must be after openingTime"); }
}
