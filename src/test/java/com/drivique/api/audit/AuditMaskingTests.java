package com.drivique.api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.drivique.api.service.AuditService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditMaskingTests {

    @Test
    void passwordsTokensAndCardDataAreNeverStored() throws Exception {
        AuditService service = new AuditService(null, null, null, null);
        Method toJson = AuditService.class.getDeclaredMethod("toJson", Object.class);
        toJson.setAccessible(true);
        Map<String, Object> payload = Map.of(
                "email", "ana@drivique.com",
                "password", "Secreta123!",
                "nested", Map.of("accessToken", "abc.def.ghi", "cardNumber", "4111111111111111", "city", "Medellín"),
                "items", List.of(Map.of("cvv", "123", "name", "x")));
        String json = (String) toJson.invoke(service, payload);
        assertThat(json).contains("ana@drivique.com").contains("Medellín").contains("***");
        assertThat(json).doesNotContain("Secreta123!").doesNotContain("abc.def.ghi").doesNotContain("4111111111111111").doesNotContain("\"123\"");
    }
}
