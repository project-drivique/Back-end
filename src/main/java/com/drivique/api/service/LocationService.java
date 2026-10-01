package com.drivique.api.service;

import com.drivique.api.dto.CityRequestDTO;
import com.drivique.api.dto.CityResponseDTO;
import com.drivique.api.dto.DepartmentResponseDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.mapper.LocationMapper;
import com.drivique.api.model.City;
import com.drivique.api.model.Department;
import com.drivique.api.repository.CityRepository;
import com.drivique.api.repository.DepartmentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService {
    private final DepartmentRepository departments;
    private final CityRepository cities;

    public LocationService(DepartmentRepository departments, CityRepository cities) {
        this.departments = departments;
        this.cities = cities;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponseDTO> departments() {
        return departments.findByActiveTrueOrderByNameAsc().stream().map(LocationMapper::department).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable("activeCities")
    public List<CityResponseDTO> cities() {
        return cities.findByActiveTrueOrderByNameAsc().stream().map(LocationMapper::city).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "departmentCities", key = "#departmentId")
    public List<CityResponseDTO> citiesByDepartment(UUID departmentId) {
        activeDepartment(departmentId);
        return cities.findByDepartmentIdAndActiveTrueOrderByNameAsc(departmentId).stream()
                .map(LocationMapper::city)
                .toList();
    }

    @Transactional
    @CacheEvict(value = {"activeCities", "departmentCities"}, allEntries = true)
    public CityResponseDTO create(CityRequestDTO input) {
        Department department = activeDepartment(input.departmentId());
        String name = input.name().strip();
        if (cities.existsByDepartmentIdAndNameIgnoreCase(department.getId(), name)) {
            throw new ConflictException("City already exists in the department");
        }
        try {
            return LocationMapper.city(cities.saveAndFlush(new City(department, name, input.hasAirport(), input.hasTerminal())));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("City conflicts with database constraints");
        }
    }

    @Transactional
    @CacheEvict(value = {"activeCities", "departmentCities"}, allEntries = true)
    public CityResponseDTO update(UUID cityId, CityRequestDTO input) {
        City city = cities.findById(cityId).orElseThrow(() -> new ResourceNotFoundException("City not found"));
        Department department = activeDepartment(input.departmentId());
        String name = input.name().strip();
        if (cities.existsByDepartmentIdAndNameIgnoreCaseAndIdNot(department.getId(), name, cityId)) {
            throw new ConflictException("City already exists in the department");
        }
        city.update(department, name, input.hasAirport(), input.hasTerminal());
        try {
            return LocationMapper.city(cities.saveAndFlush(city));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("City conflicts with database constraints");
        }
    }

    private Department activeDepartment(UUID id) {
        return departments.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Active department not found"));
    }
}
