package com.drivique.api.controller;
import com.drivique.api.dto.*;
import com.drivique.api.service.MileagePlanService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/v1/mileage-plans") public class MileagePlanController {
 private final MileagePlanService service; public MileagePlanController(MileagePlanService service){this.service=service;}
 @GetMapping public List<MileagePlanResponseDTO> active(){return service.active();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('SUPER_ADMIN')") public MileagePlanResponseDTO create(@Valid @RequestBody MileagePlanRequestDTO input){return service.create(input);}
 @PutMapping("/{id}") @PreAuthorize("hasRole('SUPER_ADMIN')") public MileagePlanResponseDTO update(@PathVariable UUID id,@Valid @RequestBody MileagePlanRequestDTO input){return service.update(id,input);}
}
