package com.drivique.api.service;

import com.drivique.api.dto.*;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VehicleInspectionService {
    private final RentalContractRepository contracts; private final VehicleInspectionRepository inspections;
    private final InspectionChecklistItemRepository checklistItems; private final InspectionChecklistAnswerRepository answers;
    private final UserRepository users; private final FileStorageService storage; private final ContractStatusRepository contractStatuses;
    private final ReservationStatusRepository reservationStatuses; private final BigDecimal fuelRate; private final BigDecimal damageRate;
    public VehicleInspectionService(RentalContractRepository contracts, VehicleInspectionRepository inspections, InspectionChecklistItemRepository checklistItems, InspectionChecklistAnswerRepository answers, UserRepository users, FileStorageService storage, ContractStatusRepository contractStatuses, ReservationStatusRepository reservationStatuses, @Value("${app.inspection.fuel-charge-per-percent:5000}") BigDecimal fuelRate, @Value("${app.inspection.damage-charge-per-item:200000}") BigDecimal damageRate) {
        this.contracts=contracts; this.inspections=inspections; this.checklistItems=checklistItems; this.answers=answers; this.users=users; this.storage=storage; this.contractStatuses=contractStatuses; this.reservationStatuses=reservationStatuses; this.fuelRate=fuelRate; this.damageRate=damageRate;
    }
    @Transactional
    public VehicleInspectionResponseDTO register(UUID contractId, VehicleInspectionRequestDTO request, List<MultipartFile> evidencePhotos, String email) {
        String type=request.inspectionType().trim().toUpperCase(Locale.ROOT); RentalContract contract=contracts.findById(contractId).orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado")); User inspector=user(email); validateStaff(inspector);
        if(inspections.existsByContractIdAndInspectionType(contractId,type)) throw new ConflictException("Ya existe una inspección "+type+" para este contrato.");
        if(contract.getSignedAt()==null) throw new ConflictException("El contrato debe estar firmado antes de registrar la entrega.");
        VehicleInspection checkIn=null;
        if("CHECK_OUT".equals(type)){checkIn=inspections.findByContractIdAndInspectionType(contractId,"CHECK_IN").orElseThrow(() -> new ConflictException("Debe registrar el CHECK_IN antes del CHECK_OUT."));if(request.mileage()<checkIn.getMileage())throw new IllegalArgumentException("El kilometraje de devolución no puede ser menor al registrado en la entrega.");}
        if(request.mileage()<contract.getVehicle().getMileage()) throw new IllegalArgumentException("El kilometraje no puede ser menor al kilometraje actual del vehículo.");
        VehicleInspection inspection=new VehicleInspection(contract,type,inspector,request.mileage(),request.fuelLevelPercent(),blankToNull(request.observations())); Set<UUID> answeredItems=new HashSet<>(); List<MultipartFile> photos=evidencePhotos==null?List.of():evidencePhotos;
        for(InspectionChecklistAnswerRequestDTO ar:request.answers()){
            if(!answeredItems.add(ar.checklistItemId()))throw new IllegalArgumentException("No puede repetir un ítem de checklist."); InspectionChecklistItem item=checklistItems.findById(ar.checklistItemId()).orElseThrow(() -> new ResourceNotFoundException("Ítem de checklist no encontrado")); if(!item.isActive())throw new ConflictException("El ítem de checklist está inactivo.");
            String observation=blankToNull(ar.observation()),url=null,hash=null; if(!ar.compliant()&&observation==null)throw new IllegalArgumentException("Cada ítem no conforme debe incluir una observación.");
            if(!ar.compliant()||ar.evidencePhotoIndex()!=null){MultipartFile evidence=photoAt(photos,ar.evidencePhotoIndex());validatePhoto(evidence);try{hash=sha256(evidence.getBytes());}catch(Exception ex){throw new IllegalArgumentException("No se pudo leer la evidencia.");}url=storage.storeFile(evidence,"contracts/inspections");}
            inspection.addAnswer(new InspectionChecklistAnswer(inspection,item,ar.compliant(),observation,url,hash));
        }
        Integer kmDiff=null; BigDecimal fuelDiff=null;
        if(checkIn!=null){kmDiff=request.mileage()-checkIn.getMileage();fuelDiff=checkIn.getFuelLevelPercent().subtract(request.fuelLevelPercent()).max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP);BigDecimal mileage=calculateExtraMileageCharge(contract.getReservation().getMileagePlan(),kmDiff);BigDecimal fuel=fuelDiff.multiply(fuelRate).setScale(2,RoundingMode.HALF_UP);long damaged=inspection.getAnswers().stream().filter(a -> !a.isCompliant()).count();BigDecimal damage=damageRate.multiply(BigDecimal.valueOf(damaged)).setScale(2,RoundingMode.HALF_UP);inspection.setCharges(mileage,fuel,damage);contract.setAdditionalCharges(inspection.getTotalCharge());contract.setStatus(contractStatuses.findByCodeIgnoreCase("FINALIZED").orElseThrow(() -> new ResourceNotFoundException("Estado FINALIZED no configurado")));contract.getReservation().setStatus(reservationStatuses.findByCodeIgnoreCase("COMPLETED").orElseThrow(() -> new ResourceNotFoundException("Estado COMPLETED no configurado")));contract.getVehicle().setMileage(request.mileage());}
        else{contract.getReservation().setStatus(reservationStatuses.findByCodeIgnoreCase("IN_PROGRESS").orElseThrow(() -> new ResourceNotFoundException("Estado IN_PROGRESS no configurado")));}
        return map(inspections.saveAndFlush(inspection),kmDiff,fuelDiff);
    }
    @Transactional(readOnly=true) public List<VehicleInspectionResponseDTO> list(UUID contractId,String email){RentalContract c=contracts.findById(contractId).orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado"));validateAccess(c,user(email));return inspections.findByContractIdOrderByCreatedAtAsc(contractId).stream().map(i -> map(i,null,null)).toList();}
    @Transactional(readOnly=true) public List<InspectionChecklistItemResponseDTO> checklist(){return checklistItems.findAll().stream().filter(InspectionChecklistItem::isActive).map(i -> new InspectionChecklistItemResponseDTO(i.getId(),i.getName(),i.getDescription())).toList();}
    @Transactional(readOnly=true) public byte[] evidence(UUID answerId,String email){InspectionChecklistAnswer a=answers.findById(answerId).orElseThrow(() -> new ResourceNotFoundException("Evidencia no encontrada"));validateAccess(a.getInspection().getContract(),user(email));if(a.getEvidencePhotoUrl()==null)throw new ResourceNotFoundException("Evidencia no encontrada");byte[] content=storage.read(a.getEvidencePhotoUrl());if(a.getEvidenceSha256()!=null&&!a.getEvidenceSha256().equals(sha256(content)))throw new ConflictException("La integridad de la evidencia no es válida.");return content;}
    private User user(String e){return users.findByEmailIgnoreCaseAndDeletedAtIsNull(e).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));} private void validateStaff(User u){if(!isStaff(u))throw new ResourceNotFoundException("Contrato no encontrado");} private void validateAccess(RentalContract c,User u){if(!c.getCustomer().getId().equals(u.getId())&&!isStaff(u))throw new ResourceNotFoundException("Contrato no encontrado");} private boolean isStaff(User u){return u.getRoles()!=null&&u.getRoles().stream().anyMatch(r -> List.of("ADMIN","SUPER_ADMIN","AGENT","EMPLOYEE","BRANCH_ADMIN").contains(r.getCode()));}
    private BigDecimal calculateExtraMileageCharge(MileagePlan p,int d){if(p==null||p.getIncludedKm()==null||d<=p.getIncludedKm())return BigDecimal.ZERO.setScale(2);return p.getExtraKmRate().multiply(BigDecimal.valueOf(d-p.getIncludedKm())).setScale(2,RoundingMode.HALF_UP);} private MultipartFile photoAt(List<MultipartFile> p,Integer i){if(i==null||i<0||i>=p.size())throw new IllegalArgumentException("Cada ítem no conforme debe incluir una foto de evidencia válida.");return p.get(i);} private void validatePhoto(MultipartFile f){String t=f==null?null:f.getContentType();if(t==null||!(t.equalsIgnoreCase("image/jpeg")||t.equalsIgnoreCase("image/jpg")||t.equalsIgnoreCase("image/png")))throw new IllegalArgumentException("La evidencia debe ser JPG o PNG.");storage.validateFile(f);} private String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();} private String sha256(byte[] v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v));}catch(Exception e){throw new IllegalStateException(e);}}
    private VehicleInspectionResponseDTO map(VehicleInspection i,Integer km,BigDecimal fuel){List<InspectionChecklistAnswerResponseDTO> a=i.getAnswers().stream().map(x -> new InspectionChecklistAnswerResponseDTO(x.getId(),x.getChecklistItem().getId(),x.getChecklistItem().getName(),x.isCompliant(),x.getObservation(),x.getEvidencePhotoUrl(),x.getEvidenceSha256())).toList();return new VehicleInspectionResponseDTO(i.getId(),i.getContract().getId(),i.getInspectionType(),i.getMileage(),i.getFuelLevelPercent(),i.getObservations(),i.getCreatedAt(),km,fuel,i.getMileageCharge(),i.getFuelCharge(),i.getDamageCharge(),i.getTotalCharge(),a);}
}
