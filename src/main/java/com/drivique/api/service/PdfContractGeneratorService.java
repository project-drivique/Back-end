package com.drivique.api.service;

import com.drivique.api.model.RentalContract;
import java.io.ByteArrayOutputStream;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

@Service
public class PdfContractGeneratorService {
    public byte[] generate(RentalContract contract, byte[] signature) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 48, 48, 48, 48);
            PdfWriter.getInstance(document, output);
            document.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            document.add(new Paragraph("DRIVIQUE", title));
            document.add(new Paragraph("Contrato de arrendamiento " + contract.getContractNumber()));
            document.add(new Paragraph("Cliente: " + contract.getCustomer().getFirstName() + " " + contract.getCustomer().getLastName()));
            document.add(new Paragraph("Vehículo: " + contract.getVehicle().getModel() + " — " + contract.getVehicle().getPlate()));
            document.add(new Paragraph("Periodo: " + contract.getScheduledStartAt() + " a " + contract.getScheduledEndAt()));
            document.add(new Paragraph("Tarifa base: " + contract.getBaseAmount() + " | Depósito: " + contract.getSecurityDeposit()));
            document.add(new Paragraph("\nCláusulas aplicables:"));
            contract.getClauses().forEach(clause -> document.add(new Paragraph(clause.getSortOrder() + ". " + clause.getTitle() + ": " + clause.getContent())));
            document.add(new Paragraph("\nFirma biométrica del cliente:"));
            Image image = Image.getInstance(signature);
            image.scaleToFit(180, 80);
            document.add(image);
            document.add(new Paragraph("Firma registrada electrónicamente en Drivique."));
            document.close();
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible generar el PDF del contrato.", e);
        }
    }
}
