package com.drivique.api.user.entity;

import com.drivique.api.auth.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preferences", schema = "iam")
public class UserPreference {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "language_id")
    private UUID languageId;

    @Column(name = "currency_id")
    private UUID currencyId;

    @Column(name = "theme_preference", nullable = false, length = 20)
    private String themePreference = "SYSTEM";

    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications = true;

    @Column(name = "sms_notifications", nullable = false)
    private boolean smsNotifications = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserPreference() {}

    public UserPreference(User user) {
        this.user = user;
        this.userId = user != null ? user.getId() : null;
        this.themePreference = "SYSTEM";
        this.emailNotifications = true;
        this.smsNotifications = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public User getUser() { return user; }
    public void setUser(User user) {
        this.user = user;
        if (user != null) {
            this.userId = user.getId();
        }
    }

    public UUID getLanguageId() { return languageId; }
    public void setLanguageId(UUID languageId) { this.languageId = languageId; }

    public UUID getCurrencyId() { return currencyId; }
    public void setCurrencyId(UUID currencyId) { this.currencyId = currencyId; }

    public String getThemePreference() { return themePreference; }
    public void setThemePreference(String themePreference) { this.themePreference = themePreference; }

    public boolean isEmailNotifications() { return emailNotifications; }
    public void setEmailNotifications(boolean emailNotifications) { this.emailNotifications = emailNotifications; }

    public boolean isSmsNotifications() { return smsNotifications; }
    public void setSmsNotifications(boolean smsNotifications) { this.smsNotifications = smsNotifications; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
