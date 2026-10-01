package com.drivique.api.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "compliance")
public class ConsentRequirementsProperties {
    private List<Requirement> requiredConsents = new ArrayList<>();
    public List<Requirement> getRequiredConsents() { return requiredConsents; }
    public void setRequiredConsents(List<Requirement> values) {
        requiredConsents = values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
    public static class Requirement {
        private String consentType;
        private String documentVersion;
        public String getConsentType() { return consentType; }
        public void setConsentType(String value) { consentType = value; }
        public String getDocumentVersion() { return documentVersion; }
        public void setDocumentVersion(String value) { documentVersion = value; }
    }
}
