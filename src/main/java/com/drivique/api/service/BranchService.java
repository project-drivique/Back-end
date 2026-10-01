package com.drivique.api.service;
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
    public BranchService(BranchRepository branches, CityRepository cities) { this.branches = branches; this.cities = cities; }
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
        try { return BranchMapper.branch(branches.saveAndFlush(new Branch(name, input.address().strip(), activeCity(input.cityId()), input.phone().strip(), input.openingTime(), input.closingTime(), input.allowsCashPayment()))); }
        catch (DataIntegrityViolationException exception) { throw new ConflictException("Branch conflicts with database constraints"); }
    }
    @Transactional public BranchResponseDTO update(UUID id, BranchRequestDTO input) {
        validateHours(input); Branch branch = branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        String name = input.name().strip(); if (branches.existsByNameIgnoreCaseAndIdNot(name, id)) throw new ConflictException("Branch name already exists");
        branch.update(name, input.address().strip(), activeCity(input.cityId()), input.phone().strip(), input.openingTime(), input.closingTime(), input.allowsCashPayment());
        try { return BranchMapper.branch(branches.saveAndFlush(branch)); } catch (DataIntegrityViolationException exception) { throw new ConflictException("Branch conflicts with database constraints"); }
    }
    @Transactional public BranchResponseDTO toggle(UUID id) {
        Branch branch = branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Branch not found")); branch.toggleStatus(); return BranchMapper.branch(branch);
    }
    private City activeCity(UUID id) { return cities.findById(id).filter(City::isActive).orElseThrow(() -> new ResourceNotFoundException("Active city not found")); }
    private void validateHours(BranchRequestDTO input) { if (!input.closingTime().isAfter(input.openingTime())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "closingTime must be after openingTime"); }
}
