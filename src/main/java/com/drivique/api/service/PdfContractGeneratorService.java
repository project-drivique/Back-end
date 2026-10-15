package com.drivique.api.service;

import com.drivique.api.model.Branch;
import com.drivique.api.model.RentalContract;
import com.drivique.api.model.Reservation;
import com.drivique.api.model.User;
import com.drivique.api.model.Vehicle;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Genera el PDF del contrato con la misma plantilla que ve el cliente al firmar. */
@Service
public class PdfContractGeneratorService {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final Locale ES = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES).withZone(ZONE);

    private static final Color INK = new Color(15, 23, 42);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color BORDER = new Color(226, 232, 240);
    private static final Color SOFT = new Color(248, 250, 252);
    private static final Color BRAND = new Color(30, 58, 138);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, INK);
    private static final Font SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, MUTED);
    private static final Font BADGE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, BRAND);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, INK);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, MUTED);
    private static final Font VALUE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, INK);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10, INK);
    private static final Font BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, INK);
    private static final Font NOTE = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);

    public byte[] generate(RentalContract contract, byte[] signature) {
        return generate(contract, signature, ContractPdfExtras.EMPTY);
    }

    public byte[] generate(RentalContract contract, byte[] signature, ContractPdfExtras extras) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(document, output);
            document.open();

            User customer = contract.getCustomer();
            Reservation reservation = contract.getReservation();
            Vehicle vehicle = contract.getVehicle();
            Branch branch = contract.getPickupBranch();
            boolean signed = contract.getSignedAt() != null;
            String fullName = customer == null ? "" : join(customer.getFirstName(), customer.getLastName());
            String documentType = text(extras.documentTypeName());
            String documentNumber = customer == null ? "" : text(customer.getDocumentNumber());

            addHeader(document, contract, reservation, signed);

            Paragraph intro = new Paragraph("Entre LA PLATAFORMA, Drivique, y EL USUARIO, " + fullName
                    + ", identificado(a) con " + join(documentType, documentNumber)
                    + ", se celebra el presente contrato de reserva y alquiler de vehículo, generado automáticamente a partir de la información registrada en el sistema.", BODY);
            intro.setSpacingBefore(14);
            intro.setSpacingAfter(10);
            document.add(intro);

            addSection(document, "Datos del usuario", fields(
                    "Nombre completo", fullName,
                    "Documento", documentType.isBlank() ? documentNumber : documentType + ": " + documentNumber,
                    "Correo", customer == null ? "" : text(customer.getEmail()),
                    "Teléfono", customer == null ? "" : text(customer.getPhone()),
                    "Dirección", customer == null ? "" : text(customer.getAddress()),
                    "Licencia", text(extras.licenseStatusText())));

            String vehicleName = vehicle == null ? "" : join(brandName(vehicle), vehicle.getModel());
            String branchCity = branch != null && branch.getCity() != null ? branch.getCity().getName() : "";
            boolean cash = "CASH".equalsIgnoreCase(extras.paymentMethodCode())
                    || (reservation != null && reservation.getCashPaymentCode() != null && extras.paymentMethodCode() == null);
            addSection(document, "Reserva y vehículo", fields(
                    "Vehículo", vehicleName,
                    "Placa", vehicle == null ? "" : text(vehicle.getPlate()),
                    "Color", vehicle == null ? "" : text(vehicle.getColor()),
                    "Año", vehicle == null ? "" : String.valueOf(vehicle.getYear()),
                    "Sucursal", branch == null ? "" : text(branch.getName()),
                    "Ciudad de sucursal", branchCity,
                    "Dirección de Sucursal", branch == null ? "" : text(branch.getAddress()),
                    "Fecha inicio", date(contract.getScheduledStartAt()),
                    "Fecha fin", date(contract.getScheduledEndAt()),
                    "Método de pago", cash ? "Pago físico en sucursal" : "Wompi",
                    "Punto de pago en efectivo", cash && reservation != null && reservation.getCashPaymentBranch() != null
                            ? text(reservation.getCashPaymentBranch().getName()) : "",
                    "Valor total", money(total(contract)),
                    "Servicios adicionales", services(reservation),
                    "Plan de protección", reservation == null || reservation.getInsuranceCoverage() == null
                            ? "" : text(reservation.getInsuranceCoverage().getName())));

            addClauses(document, vehicle, contract);
            addSignatures(document, contract, signature, fullName, documentType, documentNumber, branchCity);

            Paragraph note = new Paragraph("Documento generado automáticamente por Drivique.\nUso académico y funcional dentro del flujo de reserva y contratación digital.", NOTE);
            note.setSpacingBefore(14);
            note.setAlignment(Element.ALIGN_CENTER);
            document.add(note);

            document.close();
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible generar el PDF del contrato.", e);
        }
    }

    private void addHeader(Document document, RentalContract contract, Reservation reservation, boolean signed) throws Exception {
        PdfPTable header = new PdfPTable(new float[] {62, 38});
        header.setWidthPercentage(100);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        PdfPTable brand = new PdfPTable(new float[] {14, 86});
        brand.setWidthPercentage(100);
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        Image logo = resource("/contract/logo.png");
        if (logo != null) {
            logo.scaleToFit(46, 46);
            logoCell.addElement(logo);
        }
        brand.addCell(logoCell);
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.addElement(new Paragraph("Contrato de reserva y alquiler", TITLE));
        titleCell.addElement(new Paragraph("Drivique · Plataforma académica de alquiler de vehículos", SUBTITLE));
        titleCell.addElement(new Paragraph("Documento autogenerado con los datos de tu reserva.", SUBTITLE));
        brand.addCell(titleCell);
        left.addElement(brand);
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorderColor(BORDER);
        right.setBackgroundColor(SOFT);
        right.setPadding(10);
        right.addElement(new Paragraph("DOCUMENTO DIGITAL", BADGE));
        right.addElement(labelled("Código contrato: ", text(contract.getContractNumber())));
        right.addElement(labelled("Estado: ", signed ? "Firmado" : "Pendiente de firma"));
        right.addElement(labelled("Fecha de generación: ", DATE.format(Instant.now())));
        right.addElement(labelled("Reserva: ", reservation == null ? "" : text(reservation.getCode())));
        header.addCell(right);
        document.add(header);
    }

    private Paragraph labelled(String label, String value) {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(3);
        p.add(new com.lowagie.text.Chunk(label, BODY_BOLD));
        p.add(new com.lowagie.text.Chunk(value, BODY));
        return p;
    }

    private void addSection(Document document, String title, List<String[]> fields) throws Exception {
        Paragraph heading = new Paragraph(title, H2);
        heading.setSpacingBefore(12);
        heading.setSpacingAfter(6);
        document.add(heading);
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        for (String[] field : fields) {
            table.addCell(fieldCell(field[0], field[1]));
        }
        if (fields.size() % 2 == 1) {
            PdfPCell empty = new PdfPCell(new Phrase(" "));
            empty.setBorderColor(BORDER);
            empty.setBackgroundColor(SOFT);
            table.addCell(empty);
        }
        document.add(table);
    }

    private PdfPCell fieldCell(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(BORDER);
        cell.setBorderWidth(0.8f);
        cell.setBackgroundColor(Color.WHITE);
        cell.setPadding(8);
        cell.addElement(new Paragraph(label.toUpperCase(ES), LABEL));
        Paragraph v = new Paragraph(value == null || value.isBlank() ? "—" : value, VALUE);
        v.setSpacingBefore(3);
        cell.addElement(v);
        return cell;
    }

    private void addClauses(Document document, Vehicle vehicle, RentalContract contract) throws Exception {
        Paragraph heading = new Paragraph("Cláusulas", H2);
        heading.setSpacingBefore(14);
        heading.setSpacingAfter(6);
        document.add(heading);

        String period = date(contract.getScheduledStartAt()) + " y " + date(contract.getScheduledEndAt());
        String vehicleText = vehicle == null ? "" : join(brandName(vehicle), vehicle.getModel()) + ", placa " + text(vehicle.getPlate());
        clause(document, "1. Objeto.", "Este contrato regula la reserva y el uso temporal del vehículo " + vehicleText
                + ", para el periodo comprendido entre " + period + ".");
        clause(document, "2. Generación automática.", "El contrato se crea de forma automática con los datos personales del usuario, la información del vehículo, la sucursal seleccionada, la ciudad de la sucursal, el método de pago y el valor total de la reserva. EL USUARIO solo debe revisar la información y firmar.");
        clause(document, "3. Obligaciones del usuario.", "");
        for (String item : List.of(
                "Entregar información veraz, vigente y completa.",
                "Usar el vehículo de manera responsable y conforme a la normatividad aplicable.",
                "Responder por daños, multas o incumplimientos atribuibles a su uso.",
                "Devolver el vehículo en el tiempo y lugar acordados, salvo modificación aprobada en el sistema.")) {
            Paragraph li = new Paragraph("•  " + item, BODY);
            li.setIndentationLeft(16);
            li.setSpacingAfter(2);
            document.add(li);
        }
        clause(document, "4. Obligaciones de la plataforma.", "Drivique deberá generar, mostrar y almacenar este contrato asociado a la reserva, permitiendo su revisión antes de la firma y su conservación en formato digital.");
        clause(document, "5. Tratamiento de datos.", "EL USUARIO autoriza el uso de sus datos para gestionar la reserva, generar el contrato, registrar la firma, almacenar evidencia digital y mantener trazabilidad del proceso.");
        clause(document, "6. Naturaleza académica.", "Drivique corresponde a una plataforma académica desarrollada con fines de análisis, diseño e implementación de un sistema digital de alquiler de vehículos.");
    }

    private void clause(Document document, String title, String body) throws Exception {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(4);
        p.setSpacingAfter(3);
        p.add(new com.lowagie.text.Chunk(title + (body.isEmpty() ? "" : " "), BODY_BOLD));
        if (!body.isEmpty()) p.add(new com.lowagie.text.Chunk(body, BODY));
        document.add(p);
    }

    private void addSignatures(Document document, RentalContract contract, byte[] signature, String fullName,
            String documentType, String documentNumber, String branchCity) throws Exception {
        Paragraph heading = new Paragraph("Firmas", H2);
        heading.setSpacingBefore(14);
        heading.setSpacingAfter(6);
        document.add(heading);

        String signCity = contract.getSignedCity() != null ? contract.getSignedCity().getName() : branchCity;
        Instant signedAt = contract.getSignedAt() != null ? contract.getSignedAt() : Instant.now();
        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        info.addCell(fieldCell("Ciudad de firma", signCity));
        info.addCell(fieldCell("Fecha de firma", DATE.format(signedAt)));
        document.add(info);

        PdfPTable boxes = new PdfPTable(2);
        boxes.setWidthPercentage(100);
        boxes.setSpacingBefore(8);

        PdfPCell user = box("Firma del usuario");
        user.addElement(signatureImage(signature, 190, 80));
        user.addElement(labelled("Nombre completo: ", fullName));
        user.addElement(labelled("Documento: ", documentType.isBlank() ? documentNumber : documentType + ": " + documentNumber));
        boxes.addCell(user);

        PdfPCell platform = box("Firma de la plataforma");
        platform.addElement(signatureImage(resourceBytes("/contract/drivique-signature.png"), 150, 80));
        platform.addElement(labelled("Responsable: ", "Sistema Drivique"));
        platform.addElement(labelled("Rol: ", "Plataforma de arrendamiento"));
        boxes.addCell(platform);
        document.add(boxes);
    }

    private PdfPCell box(String title) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(BORDER);
        cell.setBackgroundColor(SOFT);
        cell.setPadding(10);
        cell.addElement(new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, INK)));
        return cell;
    }

    private Element signatureImage(byte[] bytes, float width, float height) {
        try {
            if (bytes != null && bytes.length > 0) {
                Image image = Image.getInstance(bytes);
                image.scaleToFit(width, height);
                image.setAlignment(Element.ALIGN_CENTER);
                Paragraph holder = new Paragraph();
                holder.setSpacingBefore(6);
                holder.setSpacingAfter(6);
                holder.add(new com.lowagie.text.Chunk(image, 0, 0));
                return holder;
            }
        } catch (Exception ignored) {
            // si la imagen no se puede leer, se deja el espacio en blanco
        }
        return new Paragraph(" ");
    }

    private List<String[]> fields(String... pairs) {
        java.util.ArrayList<String[]> list = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            list.add(new String[] {pairs[i], pairs[i + 1]});
        }
        return list;
    }

    private String services(Reservation reservation) {
        if (reservation == null || reservation.getAdditionalServices() == null || reservation.getAdditionalServices().isEmpty()) {
            return "Ninguno";
        }
        return reservation.getAdditionalServices().stream()
                .map(s -> s.getAdditionalService() != null ? s.getAdditionalService().getName() : null)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.joining(", "));
    }

    private BigDecimal total(RentalContract contract) {
        BigDecimal base = contract.getBaseAmount() == null ? BigDecimal.ZERO : contract.getBaseAmount();
        BigDecimal extra = contract.getAdditionalCharges() == null ? BigDecimal.ZERO : contract.getAdditionalCharges();
        return base.add(extra);
    }

    private String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(ES);
        format.setMaximumFractionDigits(0);
        return format.format(value);
    }

    private String date(Instant value) {
        return value == null ? "" : DATE.format(value);
    }

    private String brandName(Vehicle vehicle) {
        return vehicle.getBrand() != null ? vehicle.getBrand().getName() : "";
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String join(String... parts) {
        return java.util.Arrays.stream(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(" "));
    }

    private Image resource(String path) {
        try {
            byte[] bytes = resourceBytes(path);
            return bytes == null ? null : Image.getInstance(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] resourceBytes(String path) {
        try (InputStream in = PdfContractGeneratorService.class.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }
}
