package com.drivique.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class WompiSignatureService {
    public String integrity(String reference, long amountInCents, String currency, String secret) { return sha256(reference + amountInCents + currency + requiredSecret(secret, "integridad")); }
    public boolean validWebhook(JsonNode event, String headerChecksum, String eventSecret) {
        JsonNode signature=event.path("signature"); String checksum=headerChecksum==null||headerChecksum.isBlank()?signature.path("checksum").asText():headerChecksum;
        if(checksum.isBlank()||!signature.path("properties").isArray()) return false;
        StringBuilder payload=new StringBuilder(); for(JsonNode property:signature.path("properties")){ String value=valueAt(event,property.asText()); if(value==null) return false; payload.append(value); }
        return MessageDigest.isEqual(sha256(payload.append(requiredSecret(eventSecret,"eventos")).toString()).getBytes(StandardCharsets.UTF_8),checksum.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
    private String valueAt(JsonNode root,String path){ JsonNode node=root; for(String segment:path.split("\\.")){ node=node.path(segment); if(node.isMissingNode()){ node=root.path("data"); for(String nested:path.split("\\.")){ node=node.path(nested); } break; } } return node.isMissingNode()||node.isNull()?null:node.isValueNode()?node.asText():node.toString(); }
    private String requiredSecret(String value,String label){if(value==null||value.isBlank())throw new IllegalStateException("No está configurado el secreto de "+label+" de Wompi.");return value;}
    private String sha256(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
