package com.drivique.api.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "languages", schema = "core")
public class Language {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 10)
    private String code;
    @Column(nullable = false, unique = true, length = 50)
    private String name;
    @Column(name = "is_default", nullable = false)
    private boolean defaultLanguage;
    @Column(name = "is_active", nullable = false)
    private boolean active;
    protected Language() {}
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean getDefaultLanguage() { return defaultLanguage; }
    public boolean getActive() { return active; }
    public Language(String code, String name) { this.code = code; this.name = name; this.active = true; }
    public void toggleStatus() { active = !active; }
}
