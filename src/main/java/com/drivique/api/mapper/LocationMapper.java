package com.drivique.api.mapper;

import com.drivique.api.dto.CityResponseDTO;
import com.drivique.api.dto.DepartmentResponseDTO;
import com.drivique.api.model.City;
import com.drivique.api.model.Department;

public final class LocationMapper {
    private LocationMapper() {
    }

    public static DepartmentResponseDTO department(Department department) {
        return new DepartmentResponseDTO(department.getId(), department.getName(), department.isActive());
    }

    public static CityResponseDTO city(City city) {
        Department department = city.getDepartment();
        return new CityResponseDTO(city.getId(), department.getId(), department.getName(), city.getName(),
                city.hasAirport(), city.hasTerminal(), city.isActive());
    }
}
