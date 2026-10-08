package com.drivique.api.controller;
import com.drivique.api.dto.InspectionChecklistItemResponseDTO;
import com.drivique.api.service.VehicleInspectionService;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/v1/inspection-checklist")
public class InspectionChecklistController {
  private final VehicleInspectionService service;
  public InspectionChecklistController(VehicleInspectionService service){this.service=service;}
  @GetMapping public List<InspectionChecklistItemResponseDTO> list(){return service.checklist();}
}
