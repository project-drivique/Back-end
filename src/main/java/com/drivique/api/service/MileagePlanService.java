package com.drivique.api.service;
import com.drivique.api.dto.*;
import com.drivique.api.exception.*;
import com.drivique.api.model.MileagePlan;
import com.drivique.api.repository.MileagePlanRepository;
import java.util.*;
import org.springframework.cache.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class MileagePlanService {
 private final MileagePlanRepository plans; public MileagePlanService(MileagePlanRepository plans){this.plans=plans;}
 @Transactional(readOnly=true) @Cacheable("mileagePlans") public List<MileagePlanResponseDTO> active(){return plans.findByActiveTrueOrderByNameAsc().stream().map(this::dto).toList();}
 @Transactional @CacheEvict(value="mileagePlans",allEntries=true) public MileagePlanResponseDTO create(MileagePlanRequestDTO input){String name=input.name().strip(); if(plans.existsByNameIgnoreCase(name))throw new ConflictException("Mileage plan already exists"); return dto(plans.saveAndFlush(new MileagePlan(name,input.includedKm(),input.dailyRate(),input.extraKmRate())));}
 @Transactional @CacheEvict(value="mileagePlans",allEntries=true) public MileagePlanResponseDTO update(UUID id,MileagePlanRequestDTO input){MileagePlan plan=plans.findById(id).orElseThrow(()->new ResourceNotFoundException("Mileage plan not found"));String name=input.name().strip();if(plans.existsByNameIgnoreCaseAndIdNot(name,id))throw new ConflictException("Mileage plan already exists");plan.update(name,input.includedKm(),input.dailyRate(),input.extraKmRate());return dto(plans.saveAndFlush(plan));}
 private MileagePlanResponseDTO dto(MileagePlan p){return new MileagePlanResponseDTO(p.getId(),p.getName(),p.getIncludedKm(),p.getDailyRate(),p.getExtraKmRate(),p.isActive());}
}
