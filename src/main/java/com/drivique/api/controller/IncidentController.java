package com.drivique.api.controller;

import com.drivique.api.dto.IncidentAssignRequestDTO;
import com.drivique.api.dto.IncidentDTO;
import com.drivique.api.dto.IncidentRequestDTO;
import com.drivique.api.dto.IncidentStatusRequestDTO;
import com.drivique.api.model.IncidentAttachment;
import com.drivique.api.service.IncidentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/incidents")
public class IncidentController {

    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentDTO create(@Valid @RequestBody IncidentRequestDTO input, Authentication authentication) {
        return service.create(input, authentication.getName());
    }

    @GetMapping("/me")
    public List<IncidentDTO> mine(Authentication authentication) {
        return service.mine(authentication.getName());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','BRANCH_ADMIN','ADMIN','SUPER_ADMIN','AGENT')")
    public List<IncidentDTO> forStaff(@RequestParam(required = false) String status, @RequestParam(required = false) String priority,
            Authentication authentication) {
        return service.forStaff(authentication.getName(), status, priority);
    }

    @GetMapping("/{id}")
    public IncidentDTO detail(@PathVariable UUID id, Authentication authentication) {
        return service.detail(id, authentication.getName());
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentDTO addAttachments(@PathVariable UUID id, @RequestPart("files") List<MultipartFile> files, Authentication authentication) {
        return service.addAttachments(id, files, authentication.getName());
    }

    @PostMapping(value = "/{id}/responses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentDTO respond(@PathVariable UUID id, @RequestPart("message") String message,
            @RequestPart(value = "files", required = false) List<MultipartFile> files, Authentication authentication) {
        return service.respond(id, message, files, authentication.getName());
    }

    @PatchMapping("/{id}/status")
    public IncidentDTO status(@PathVariable UUID id, @Valid @RequestBody IncidentStatusRequestDTO input, Authentication authentication) {
        return service.changeStatus(id, input, authentication.getName());
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('EMPLOYEE','BRANCH_ADMIN','ADMIN','SUPER_ADMIN','AGENT')")
    public IncidentDTO assign(@PathVariable UUID id, @RequestBody(required = false) IncidentAssignRequestDTO input, Authentication authentication) {
        return service.assign(id, input, authentication.getName());
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @PathVariable UUID attachmentId, Authentication authentication) {
        IncidentAttachment attachment = service.attachment(id, attachmentId, authentication.getName());
        byte[] content = service.readAttachment(attachment);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(attachment.getOriginalName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store")
                .body(content);
    }
}
