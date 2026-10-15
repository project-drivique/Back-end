package com.drivique.api.controller;

import com.drivique.api.dto.NationalityResponseDTO;
import com.drivique.api.repository.NationalityRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/nationalities")
public class NationalityController {
    private final NationalityRepository nationalities;

    public NationalityController(NationalityRepository nationalities) {
        this.nationalities = nationalities;
    }

    @GetMapping
    public List<NationalityResponseDTO> list() {
        return nationalities.findByActiveTrueOrderByNameAsc().stream()
                .map(n -> new NationalityResponseDTO(n.getId(), n.getName(), n.getIsoCode()))
                .toList();
    }
}
