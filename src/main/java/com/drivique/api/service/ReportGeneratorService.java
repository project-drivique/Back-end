package com.drivique.api.service;

import com.drivique.api.dto.AdministrativeReportTypeResponseDTO;
import com.drivique.api.dto.GenerateReportRequestDTO;
import com.drivique.api.dto.GeneratedReportResponseDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.AdministrativeReportType;
import com.drivique.api.model.GeneratedReport;
import com.drivique.api.model.RentalContract;
import com.drivique.api.model.User;
import com.drivique.api.model.Vehicle;
import com.drivique.api.repository.AdministrativeReportTypeRepository;
import com.drivique.api.repository.GeneratedReportRepository;
import com.drivique.api.repository.RentalContractRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.repository.VehicleMaintenanceRepository;
import com.drivique.api.repository.VehicleRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ReportGeneratorService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportGeneratorService.class);

    private final AdministrativeReportTypeRepository reportTypeRepository;
    private final GeneratedReportRepository generatedReportRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final RentalContractRepository rentalContractRepository;
    @SuppressWarnings("unused")
    private final VehicleMaintenanceRepository maintenanceRepository;
    private final FileStorageService fileStorageService;

    public ReportGeneratorService(
            AdministrativeReportTypeRepository reportTypeRepository,
            GeneratedReportRepository generatedReportRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            RentalContractRepository rentalContractRepository,
            VehicleMaintenanceRepository maintenanceRepository,
            FileStorageService fileStorageService
    ) {
        this.reportTypeRepository = reportTypeRepository;
        this.generatedReportRepository = generatedReportRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.rentalContractRepository = rentalContractRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public List<AdministrativeReportTypeResponseDTO> getReportTypes() {
        seedReportTypesIfEmpty();
        return reportTypeRepository.findByActiveTrueOrderByCodeAsc().stream()
                .map(AdministrativeReportTypeResponseDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GeneratedReportResponseDTO> getGeneratedReports(String reportTypeCode) {
        List<GeneratedReport> reports;
        if (reportTypeCode != null && !reportTypeCode.isBlank()) {
            reports = generatedReportRepository.findByReportType_CodeIgnoreCaseOrderByGeneratedAtDesc(reportTypeCode.trim());
        } else {
            reports = generatedReportRepository.findAllByOrderByGeneratedAtDesc();
        }
        return reports.stream().map(GeneratedReportResponseDTO::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public GeneratedReportResponseDTO getReportById(UUID id) {
        GeneratedReport report = generatedReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reporte generado no encontrado con ID: " + id));
        return GeneratedReportResponseDTO.fromEntity(report);
    }

    @Transactional
    public GeneratedReportResponseDTO generateReport(GenerateReportRequestDTO request, String userEmail) {
        seedReportTypesIfEmpty();

        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con correo: " + userEmail));

        AdministrativeReportType reportType = reportTypeRepository.findByCodeIgnoreCase(request.reportType())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de reporte no configurado: " + request.reportType()));

        String format = request.format().trim().toUpperCase();
        byte[] content = generateFileContent(reportType.getCode(), format, request.startDate(), request.endDate());

        String extension = switch (format) {
            case "EXCEL" -> "xlsx";
            case "CSV" -> "csv";
            case "WORD" -> "doc";
            default -> "pdf";
        };

        String filename = reportType.getCode().toLowerCase() + "_" + LocalDate.now() + "." + extension;
        String fileUrl = fileStorageService.storeBytes(content, filename, "reports");

        String filtersJson = buildFiltersJson(request.startDate(), request.endDate());

        GeneratedReport report = new GeneratedReport(
                reportType,
                format,
                user,
                filtersJson,
                fileUrl,
                "COMPLETED"
        );

        GeneratedReport saved = generatedReportRepository.save(report);
        LOG.info("Reporte administrativo '{}' generado exitosamente en formato '{}' por '{}'",
                reportType.getCode(), format, userEmail);

        return GeneratedReportResponseDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public byte[] downloadReportContent(UUID id, String userEmail) {
        GeneratedReport report = generatedReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reporte generado no encontrado con ID: " + id));

        LocalDate startDate = null;
        LocalDate endDate = null;
        return generateFileContent(report.getReportType().getCode(), report.getFormat(), startDate, endDate);
    }

    public byte[] generateFileContent(String reportTypeCode, String format, LocalDate startDate, LocalDate endDate) {
        return switch (format) {
            case "EXCEL" -> generateExcelReport(reportTypeCode, startDate, endDate);
            case "CSV" -> generateCsvReport(reportTypeCode, startDate, endDate);
            case "WORD" -> generateWordReport(reportTypeCode, startDate, endDate);
            default -> generatePdfReport(reportTypeCode, startDate, endDate);
        };
    }

    private byte[] generatePdfReport(String type, LocalDate start, LocalDate end) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font subHeaderFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

            Paragraph title = new Paragraph("DRIVIQUE - REPORTE GERENCIAL", headerFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph subtitle = new Paragraph("Tipo: " + type + " | Generado: " + DateTimeFormatter.ISO_INSTANT.format(Instant.now()), subHeaderFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitle);
            document.add(new Paragraph("\n"));

            if ("FLEET_OCCUPANCY".equalsIgnoreCase(type)) {
                List<Vehicle> vehicles = vehicleRepository.findAll();
                PdfPTable table = new PdfPTable(6);
                table.setWidthPercentage(100);
                addPdfHeader(table, tableHeaderFont, "Placa", "Modelo", "Marca", "Categoría", "Tarifa Día", "Estado");

                for (Vehicle v : vehicles) {
                    table.addCell(new PdfPCell(new Phrase(v.getPlate() != null ? v.getPlate() : "-", cellFont)));
                    table.addCell(new PdfPCell(new Phrase(v.getModel() != null ? v.getModel() : "-", cellFont)));
                    table.addCell(new PdfPCell(new Phrase(v.getBrand() != null ? v.getBrand().getName() : "-", cellFont)));
                    table.addCell(new PdfPCell(new Phrase(v.getCategory() != null ? v.getCategory().getName() : "-", cellFont)));
                    table.addCell(new PdfPCell(new Phrase(v.getDailyRate() != null ? "$" + v.getDailyRate() : "-", cellFont)));
                    table.addCell(new PdfPCell(new Phrase(v.getStatus() != null ? v.getStatus().getName() : "ACTIVO", cellFont)));
                }
                document.add(table);
            } else if ("REVENUE_SUMMARY".equalsIgnoreCase(type)) {
                List<RentalContract> contracts = rentalContractRepository.findAll();
                PdfPTable table = new PdfPTable(5);
                table.setWidthPercentage(100);
                addPdfHeader(table, tableHeaderFont, "N° Contrato", "Cliente", "Monto Base", "Depósito", "Total Estimado");

                for (RentalContract c : contracts) {
                    table.addCell(new PdfPCell(new Phrase(c.getContractNumber(), cellFont)));
                    table.addCell(new PdfPCell(new Phrase(c.getCustomer() != null ? c.getCustomer().getFullName() : "-", cellFont)));
                    table.addCell(new PdfPCell(new Phrase("$" + c.getBaseAmount(), cellFont)));
                    table.addCell(new PdfPCell(new Phrase("$" + c.getSecurityDeposit(), cellFont)));
                    BigDecimal total = c.getBaseAmount().add(c.getSecurityDeposit());
                    table.addCell(new PdfPCell(new Phrase("$" + total, cellFont)));
                }
                document.add(table);
            } else {
                PdfPTable table = new PdfPTable(3);
                table.setWidthPercentage(100);
                addPdfHeader(table, tableHeaderFont, "Código", "Descripción", "Detalle");
                table.addCell(new PdfPCell(new Phrase(type, cellFont)));
                table.addCell(new PdfPCell(new Phrase("Reporte administrativo generado con métricas consolidadas", cellFont)));
                table.addCell(new PdfPCell(new Phrase("OK", cellFont)));
                document.add(table);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Error al generar PDF de reporte: " + e.getMessage(), e);
        }
    }

    private void addPdfHeader(PdfPTable table, Font font, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, font));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private byte[] generateExcelReport(String type, LocalDate start, LocalDate end) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Reporte");

            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);

            int rowIdx = 0;
            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.createCell(0).setCellValue("DRIVIQUE - REPORTE " + type);

            Row filterRow = sheet.createRow(rowIdx++);
            filterRow.createCell(0).setCellValue("Fecha Generación: " + LocalDate.now());
            rowIdx++; // blank row

            if ("FLEET_OCCUPANCY".equalsIgnoreCase(type)) {
                Row header = sheet.createRow(rowIdx++);
                String[] cols = {"Placa", "Modelo", "Marca", "Categoría", "Tarifa Día", "Estado"};
                for (int i = 0; i < cols.length; i++) {
                    org.apache.poi.ss.usermodel.Cell cell = header.createCell(i);
                    cell.setCellValue(cols[i]);
                    cell.setCellStyle(headerStyle);
                }

                List<Vehicle> vehicles = vehicleRepository.findAll();
                for (Vehicle v : vehicles) {
                    Row r = sheet.createRow(rowIdx++);
                    r.createCell(0).setCellValue(v.getPlate() != null ? v.getPlate() : "");
                    r.createCell(1).setCellValue(v.getModel() != null ? v.getModel() : "");
                    r.createCell(2).setCellValue(v.getBrand() != null ? v.getBrand().getName() : "");
                    r.createCell(3).setCellValue(v.getCategory() != null ? v.getCategory().getName() : "");
                    r.createCell(4).setCellValue(v.getDailyRate() != null ? v.getDailyRate().doubleValue() : 0.0);
                    r.createCell(5).setCellValue(v.getStatus() != null ? v.getStatus().getName() : "ACTIVO");
                }
            } else if ("REVENUE_SUMMARY".equalsIgnoreCase(type)) {
                Row header = sheet.createRow(rowIdx++);
                String[] cols = {"Contrato", "Cliente", "Monto Base", "Depósito", "Total"};
                for (int i = 0; i < cols.length; i++) {
                    org.apache.poi.ss.usermodel.Cell cell = header.createCell(i);
                    cell.setCellValue(cols[i]);
                    cell.setCellStyle(headerStyle);
                }

                List<RentalContract> contracts = rentalContractRepository.findAll();
                for (RentalContract c : contracts) {
                    Row r = sheet.createRow(rowIdx++);
                    r.createCell(0).setCellValue(c.getContractNumber());
                    r.createCell(1).setCellValue(c.getCustomer() != null ? c.getCustomer().getFullName() : "");
                    r.createCell(2).setCellValue(c.getBaseAmount() != null ? c.getBaseAmount().doubleValue() : 0.0);
                    r.createCell(3).setCellValue(c.getSecurityDeposit() != null ? c.getSecurityDeposit().doubleValue() : 0.0);
                    BigDecimal total = (c.getBaseAmount() != null ? c.getBaseAmount() : BigDecimal.ZERO)
                            .add(c.getSecurityDeposit() != null ? c.getSecurityDeposit() : BigDecimal.ZERO);
                    r.createCell(4).setCellValue(total.doubleValue());
                }
            } else {
                Row header = sheet.createRow(rowIdx++);
                header.createCell(0).setCellValue("Métrica");
                header.createCell(1).setCellValue("Valor");
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue("Tipo");
                r.createCell(1).setCellValue(type);
            }

            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Error al generar Excel de reporte: " + e.getMessage(), e);
        }
    }

    private byte[] generateCsvReport(String type, LocalDate start, LocalDate end) {
        StringWriter sw = new StringWriter();
        if ("FLEET_OCCUPANCY".equalsIgnoreCase(type)) {
            sw.write("Placa,Modelo,Marca,Categoria,TarifaDia,Estado\n");
            List<Vehicle> vehicles = vehicleRepository.findAll();
            for (Vehicle v : vehicles) {
                sw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%s,\"%s\"\n",
                        escapeCsv(v.getPlate()),
                        escapeCsv(v.getModel()),
                        escapeCsv(v.getBrand() != null ? v.getBrand().getName() : ""),
                        escapeCsv(v.getCategory() != null ? v.getCategory().getName() : ""),
                        v.getDailyRate() != null ? v.getDailyRate() : "0",
                        escapeCsv(v.getStatus() != null ? v.getStatus().getName() : "ACTIVO")
                ));
            }
        } else if ("REVENUE_SUMMARY".equalsIgnoreCase(type)) {
            sw.write("Contrato,Cliente,MontoBase,Deposito,Total\n");
            List<RentalContract> contracts = rentalContractRepository.findAll();
            for (RentalContract c : contracts) {
                BigDecimal total = (c.getBaseAmount() != null ? c.getBaseAmount() : BigDecimal.ZERO)
                        .add(c.getSecurityDeposit() != null ? c.getSecurityDeposit() : BigDecimal.ZERO);
                sw.write(String.format("\"%s\",\"%s\",%s,%s,%s\n",
                        escapeCsv(c.getContractNumber()),
                        escapeCsv(c.getCustomer() != null ? c.getCustomer().getFullName() : ""),
                        c.getBaseAmount(),
                        c.getSecurityDeposit(),
                        total
                ));
            }
        } else {
            sw.write("Tipo,FechaGeneracion,Estado\n");
            sw.write(String.format("\"%s\",\"%s\",\"COMPLETED\"\n", escapeCsv(type), LocalDate.now()));
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generateWordReport(String type, LocalDate start, LocalDate end) {
        StringBuilder sb = new StringBuilder();
        sb.append("DRIVIQUE - REPORTE GERENCIAL\n");
        sb.append("Tipo: ").append(type).append("\n");
        sb.append("Fecha: ").append(LocalDate.now()).append("\n\n");
        sb.append("Contenido consolidado del reporte ").append(type).append(" emitido para administración de Drivique.\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(String input) {
        if (input == null) return "";
        return input.replace("\"", "\"\"");
    }

    private String buildFiltersJson(LocalDate start, LocalDate end) {
        String s = start != null ? start.toString() : null;
        String e = end != null ? end.toString() : null;
        return String.format("{\"startDate\":%s,\"endDate\":%s}",
                s != null ? "\"" + s + "\"" : "null",
                e != null ? "\"" + e + "\"" : "null");
    }

    private void seedReportTypesIfEmpty() {
        if (reportTypeRepository.count() == 0) {
            reportTypeRepository.saveAll(List.of(
                    new AdministrativeReportType("FLEET_OCCUPANCY", "Ocupación de Flota", "Reporte analítico de disponibilidad y tasa de uso de vehículos", true),
                    new AdministrativeReportType("REVENUE_SUMMARY", "Resumen de Ingresos", "Métricas consolidadas de facturación por alquileres y servicios", true),
                    new AdministrativeReportType("AUDIT_TRAIL", "Pistas de Auditoría", "Trazabilidad de eventos transaccionales y cambios en el sistema", true),
                    new AdministrativeReportType("MAINTENANCE", "Mantenimiento Preventivo", "Registro de revisiones técnicas y alertas de kilometraje", true)
            ));
        }
    }
}
