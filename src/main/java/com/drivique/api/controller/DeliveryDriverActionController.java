package com.drivique.api.controller;

import com.drivique.api.repository.ReservationDeliveryPointRepository;
import com.drivique.api.service.BrandEmailTheme;
import com.drivique.api.service.DeliveryActionTokens;
import com.drivique.api.service.DeliveryDriverActionService;
import com.drivique.api.service.DeliveryDriverPortalViewRenderer;
import com.drivique.api.service.DeliveryLogisticsService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador HTTP para acciones del conductor desde su enlace de correo al gestionar un domicilio.
 * Delega toda la lógica de negocio y renderizado a {@link DeliveryDriverActionService}.
 */
@Hidden
@RestController
@RequestMapping("/v1/public/deliveries/action")
public class DeliveryDriverActionController {

    private final DeliveryDriverActionService actionService;

    @Autowired
    public DeliveryDriverActionController(DeliveryDriverActionService actionService) {
        this.actionService = actionService;
    }

    public DeliveryDriverActionController(DeliveryActionTokens tokens, DeliveryLogisticsService logistics) {
        this(tokens, logistics, null, null);
    }

    public DeliveryDriverActionController(
            DeliveryActionTokens tokens,
            DeliveryLogisticsService logistics,
            ReservationDeliveryPointRepository deliveryPoints,
            BrandEmailTheme brandTheme) {
        this.actionService = new DeliveryDriverActionService(
                tokens,
                logistics,
                deliveryPoints,
                new DeliveryDriverPortalViewRenderer(brandTheme, null),
                null);
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> preview(@RequestParam String token, HttpServletRequest request) {
        return actionService.preview(token, request.getRequestURI());
    }

    @PostMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> execute(@RequestParam(name = "token", required = false) String token) {
        return actionService.execute(token);
    }
}
