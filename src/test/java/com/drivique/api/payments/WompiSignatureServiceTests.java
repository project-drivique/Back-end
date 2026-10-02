package com.drivique.api.payments;

import static org.assertj.core.api.Assertions.assertThat;
import com.drivique.api.service.WompiSignatureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

class WompiSignatureServiceTests {
    private final WompiSignatureService service = new WompiSignatureService();
    @Test void createsCheckoutIntegritySignatureWithWompiConcatenation() { assertThat(service.integrity("PAY-123",9500000,"COP","secret")).isEqualTo(sha256("PAY-1239500000COPsecret")); }
    @Test void acceptsOnlyWebhookWithValidChecksum() throws Exception { String checksum=sha256("tx_123APPROVEDevent-secret"); var event=new ObjectMapper().readTree("{\"data\":{\"transaction\":{\"id\":\"tx_123\",\"status\":\"APPROVED\"}},\"signature\":{\"properties\":[\"transaction.id\",\"transaction.status\"],\"checksum\":\""+checksum+"\"}}"); assertThat(service.validWebhook(event,null,"event-secret")).isTrue(); assertThat(service.validWebhook(event,"invalid","event-secret")).isFalse(); }
    private String sha256(String source){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new AssertionError(e);}}
}
