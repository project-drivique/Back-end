package com.drivique.api.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Matriz de visibilidad del PIN de entrega a domicilio para el cliente: solo se entrega en estados
 * a los que la reserva llega después de un pago aprobado.
 */
class ReservationPinVisibilityTests {

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "PENDING_DOCUMENT_REVIEW", "PENDING_SCHEDULE", "PENDING_VERIFICATION"})
    void pendingStatesHideThePin(String status) {
        assertFalse(ReservationResponseMapper.customerMaySeeDeliveryPin(status));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED_BY_USER", "CANCELLED_BY_TIMEOUT", "CANCELLED", "REJECTED"})
    void cancelledOrRejectedWithoutPaymentHideThePin(String status) {
        assertFalse(ReservationResponseMapper.customerMaySeeDeliveryPin(status));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_SIGNATURE", "CONFIRMED", "IN_PROGRESS", "ACTIVE", "COMPLETED"})
    void statesAfterApprovedPaymentShowThePin(String status) {
        assertTrue(ReservationResponseMapper.customerMaySeeDeliveryPin(status));
    }

    @Test
    void unknownOrMissingStatusHidesThePin() {
        assertFalse(ReservationResponseMapper.customerMaySeeDeliveryPin(null));
        assertFalse(ReservationResponseMapper.customerMaySeeDeliveryPin("SOMETHING_NEW"));
    }

    @Test
    void statusMatchingIsCaseInsensitive() {
        assertTrue(ReservationResponseMapper.customerMaySeeDeliveryPin("confirmed"));
        assertFalse(ReservationResponseMapper.customerMaySeeDeliveryPin("pending_payment"));
    }
}
