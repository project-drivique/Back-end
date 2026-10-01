package com.drivique.api.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "document_statuses", schema = "iam")
public class DocumentStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    protected DocumentStatus() {}

    public DocumentStatus(String code, String name, String description) {
        this(null, code, name, description);
    }

    public DocumentStatus(UUID id, String code, String name, String description) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
