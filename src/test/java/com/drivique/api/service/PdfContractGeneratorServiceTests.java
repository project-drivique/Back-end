package com.drivique.api.service;

import com.drivique.api.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PdfContractGeneratorServiceTests {
    @Test
    void rendersAValidPdfWithTheEmbeddedPngSignature() {
        RentalContract contract = mock(RentalContract.class);
        User customer = mock(User.class); Vehicle vehicle = mock(Vehicle.class);
        ContractClause clause = mock(ContractClause.class);
        when(contract.getContractNumber()).thenReturn("CTR-2026-0001"); when(contract.getCustomer()).thenReturn(customer);
        when(customer.getFirstName()).thenReturn("Danna"); when(customer.getLastName()).thenReturn("Barrios");
        when(contract.getVehicle()).thenReturn(vehicle); when(vehicle.getModel()).thenReturn("RAV4"); when(vehicle.getPlate()).thenReturn("ABC-123");
        when(contract.getScheduledStartAt()).thenReturn(Instant.parse("2026-10-02T10:00:00Z")); when(contract.getScheduledEndAt()).thenReturn(Instant.parse("2026-10-03T10:00:00Z"));
        when(contract.getBaseAmount()).thenReturn(new BigDecimal("200000")); when(contract.getSecurityDeposit()).thenReturn(new BigDecimal("500000"));
        when(clause.getSortOrder()).thenReturn((short) 1); when(clause.getTitle()).thenReturn("Objeto"); when(clause.getContent()).thenReturn("Contrato de prueba"); when(contract.getClauses()).thenReturn(List.of(clause));
        byte[] signature = signature();
        byte[] pdf = new PdfContractGeneratorService().generate(contract, signature);
        assertThat(pdf).startsWith("%PDF".getBytes()).hasSizeGreaterThan(500);
    }

    private byte[] signature() {
        try {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
