package com.drivique.api.controller;
import com.drivique.api.dto.*; import com.drivique.api.service.WompiPaymentService; import com.fasterxml.jackson.databind.JsonNode; import jakarta.validation.Valid; import org.springframework.http.HttpStatus; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/v1/payments/wompi")
public class PaymentWebhookController { private final WompiPaymentService service; public PaymentWebhookController(WompiPaymentService service){this.service=service;}
 @PostMapping("/initiate") public WompiCheckoutResponseDTO initiate(@Valid @RequestBody WompiInitiatePaymentRequestDTO input,Authentication authentication){return service.initiate(input,authentication.getName());}
 @PostMapping("/payment-methods") @ResponseStatus(HttpStatus.CREATED) public SavedPaymentMethodResponseDTO saveToken(@Valid @RequestBody SaveWompiPaymentMethodRequestDTO input,Authentication authentication){return service.saveToken(input,authentication.getName());}
 @PostMapping("/webhook") @ResponseStatus(HttpStatus.NO_CONTENT) public void webhook(@RequestBody JsonNode event,@RequestHeader(value="X-Event-Checksum",required=false) String checksum){service.processWebhook(event,checksum);}
}
