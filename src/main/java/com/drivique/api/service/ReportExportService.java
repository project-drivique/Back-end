package com.drivique.api.service;

import com.drivique.api.dto.ReportDataDTO;
import com.drivique.api.dto.ReportDataDTO.Column;
import com.drivique.api.dto.ReportDataDTO.Metric;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Genera en el servidor los archivos PDF, Excel y CSV de un reporte ya calculado. */
@Service
public class ReportExportService {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final Locale CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Documento listo para dibujar: celdas ya formateadas y, si existen, los valores originales para Excel. */
    public record Document2(String title, String subtitle, List<String> headers, List<String> kinds,
                            List<List<String>> rows, List<List<Object>> rawRows,
                            List<Map<String, String>> kpis, List<Map<String, String>> filters) {}

    public Document2 fromData(ReportDataDTO data, List<Map<String, String>> filters) {
        List<String> headers = data.columns().stream().map(Column::label).toList();
        List<String> kinds = data.columns().stream().map(Column::kind).toList();
        List<List<String>> rows = new ArrayList<>();
        List<List<Object>> raw = new ArrayList<>();
        for (Map<String, Object> row : data.rows()) {
            List<String> cells = new ArrayList<>();
            List<Object> values = new ArrayList<>();
            for (Column column : data.columns()) {
                Object value = row.get(column.key());
                cells.add(display(value, column.kind()));
                values.add(value);
            }
            rows.add(cells);
            raw.add(values);
        }
        List<Map<String, String>> kpis = new ArrayList<>();
        for (Metric metric : data.summary()) {
            kpis.add(Map.of("label", metric.label(), "value", display(metric.value(), metric.kind())));
        }
        return new Document2(data.title(), "Sistema de gestión de alquiler de vehículos Drivique", headers, kinds, rows, raw, kpis, filters);
    }

    @SuppressWarnings("unchecked")
    public Document2 fromSnapshot(Map<String, Object> snapshot) {
        List<String> headers = ((List<Object>) snapshot.getOrDefault("headers", List.of())).stream().map(String::valueOf).toList();
        List<List<String>> rows = new ArrayList<>();
        for (Object row : (List<Object>) snapshot.getOrDefault("rows", List.of())) {
            rows.add(((List<Object>) row).stream().map(v -> v == null ? "" : String.valueOf(v)).toList());
        }
        return new Document2(String.valueOf(snapshot.getOrDefault("title", "Reporte")),
                String.valueOf(snapshot.getOrDefault("subtitle", "")), headers, headers.stream().map(h -> "text").toList(), rows, null,
                (List<Map<String, String>>) snapshot.getOrDefault("kpis", List.of()),
                (List<Map<String, String>>) snapshot.getOrDefault("filtersSummary", List.of()));
    }

    /** Estructura que guarda el historial: mismas claves que usa la pantalla de reportes. */
    public Map<String, Object> snapshot(Document2 doc) {
        return Map.of("title", doc.title(), "subtitle", doc.subtitle(), "headers", doc.headers(), "rows", doc.rows(),
                "kpis", doc.kpis(), "filtersSummary", doc.filters());
    }

    public byte[] render(Document2 doc, String format) {
        try {
            return switch (format.toUpperCase()) {
                case "CSV" -> csv(doc);
                case "EXCEL" -> excel(doc);
                case "PDF" -> pdf(doc);
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de exportación inválido: " + format);
            };
        } catch (IOException | DocumentException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el archivo del reporte.");
        }
    }

    public String contentType(String format) {
        return switch (format.toUpperCase()) {
            case "CSV" -> "text/csv; charset=UTF-8";
            case "EXCEL" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/pdf";
        };
    }

    public String extension(String format) {
        return switch (format.toUpperCase()) {
            case "CSV" -> "csv";
            case "EXCEL" -> "xlsx";
            default -> "pdf";
        };
    }

    // ---------------------------------------------------------------------------------------------

    private byte[] csv(Document2 doc) {
        StringBuilder out = new StringBuilder("﻿");
        out.append(csvLine(List.of(doc.title()))).append(csvLine(List.of("Generado", OffsetDateTime.now(ZONE).format(DATE_TIME))));
        for (Map<String, String> filter : doc.filters()) {
            out.append(csvLine(List.of(filter.get("label"), filter.get("value"))));
        }
        for (Map<String, String> kpi : doc.kpis()) {
            out.append(csvLine(List.of(kpi.get("label"), kpi.get("value"))));
        }
        out.append(csvLine(doc.headers()));
        for (List<String> row : doc.rows()) {
            out.append(csvLine(row));
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvLine(List<String> cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            line.append(i == 0 ? "" : ",").append(csvCell(cells.get(i)));
        }
        return line.append("\r\n").toString();
    }

    /** Escapa comillas y neutraliza fórmulas (=, +, -, @) para que el archivo no ejecute nada al abrirse en Excel. */
    private static String csvCell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private byte[] excel(Document2 doc) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Reporte");
            XSSFFont bold = workbook.createFont();
            bold.setBold(true);
            CellStyle head = workbook.createCellStyle();
            head.setFont(bold);
            int r = 0;
            sheet.createRow(r++).createCell(0).setCellValue(doc.title());
            sheet.getRow(0).getCell(0).setCellStyle(head);
            sheet.createRow(r++).createCell(0).setCellValue("Generado: " + OffsetDateTime.now(ZONE).format(DATE_TIME));
            for (Map<String, String> filter : doc.filters()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(filter.get("label"));
                row.createCell(1).setCellValue(filter.get("value"));
            }
            for (Map<String, String> kpi : doc.kpis()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(kpi.get("label"));
                row.createCell(1).setCellValue(kpi.get("value"));
                row.getCell(0).setCellStyle(head);
            }
            r++;
            Row header = sheet.createRow(r++);
            for (int c = 0; c < doc.headers().size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(doc.headers().get(c));
                cell.setCellStyle(head);
            }
            for (int i = 0; i < doc.rows().size(); i++) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < doc.headers().size(); c++) {
                    Object raw = doc.rawRows() == null ? null : doc.rawRows().get(i).get(c);
                    String kind = doc.kinds().get(c);
                    Cell cell = row.createCell(c);
                    if (raw instanceof Number n && !"date".equals(kind)) {
                        cell.setCellValue(n.doubleValue());
                    } else {
                        // Texto: Excel no lo interpreta como fórmula
                        cell.setCellValue(doc.rows().get(i).get(c));
                    }
                }
            }
            for (int c = 0; c < doc.headers().size(); c++) {
                sheet.autoSizeColumn(c);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] pdf(Document2 doc) throws DocumentException, IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document pdf = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
            PdfWriter.getInstance(pdf, out);
            pdf.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(30, 58, 138));
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font label = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

            pdf.add(new Paragraph(doc.title(), title));
            pdf.add(new Paragraph(doc.subtitle() + " • Generado " + OffsetDateTime.now(ZONE).format(DATE_TIME), small));
            for (Map<String, String> filter : doc.filters()) {
                pdf.add(new Paragraph(filter.get("label") + ": " + filter.get("value"), small));
            }
            pdf.add(new Paragraph(" "));
            if (!doc.kpis().isEmpty()) {
                PdfPTable kpis = new PdfPTable(Math.min(5, doc.kpis().size()));
                kpis.setWidthPercentage(100);
                for (Map<String, String> kpi : doc.kpis()) {
                    PdfPCell cell = new PdfPCell();
                    cell.setPadding(6);
                    cell.setBackgroundColor(new Color(239, 246, 255));
                    cell.addElement(new Paragraph(kpi.get("label"), small));
                    cell.addElement(new Paragraph(kpi.get("value"), label));
                    kpis.addCell(cell);
                }
                kpis.completeRow();
                pdf.add(kpis);
                pdf.add(new Paragraph(" "));
            }
            PdfPTable table = new PdfPTable(Math.max(1, doc.headers().size()));
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String header : doc.headers()) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headFont));
                cell.setBackgroundColor(new Color(30, 58, 138));
                cell.setPadding(5);
                table.addCell(cell);
            }
            for (List<String> row : doc.rows()) {
                for (int c = 0; c < row.size(); c++) {
                    PdfPCell cell = new PdfPCell(new Phrase(row.get(c), cellFont));
                    cell.setPadding(4);
                    String kind = doc.kinds().get(c);
                    if ("money".equals(kind) || "number".equals(kind) || "percent".equals(kind)) {
                        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    }
                    table.addCell(cell);
                }
            }
            if (doc.rows().isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("Sin registros para los filtros seleccionados", cellFont));
                empty.setColspan(Math.max(1, doc.headers().size()));
                empty.setPadding(8);
                table.addCell(empty);
            }
            pdf.add(table);
            pdf.close();
            return out.toByteArray();
        }
    }

    // ---------------------------------------------------------------------------------------------

    static String display(Object value, String kind) {
        if (value == null) {
            return "";
        }
        if ("date".equals(kind)) {
            try {
                return OffsetDateTime.parse(String.valueOf(value)).atZoneSameInstant(ZONE).format(DATE_TIME);
            } catch (RuntimeException e) {
                return String.valueOf(value);
            }
        }
        if (value instanceof Number n) {
            return switch (kind) {
                case "money" -> "$" + NumberFormat.getIntegerInstance(CO).format(Math.round(n.doubleValue()));
                case "percent" -> decimal(n.doubleValue()) + "%";
                default -> n.doubleValue() == Math.rint(n.doubleValue())
                        ? NumberFormat.getIntegerInstance(CO).format(n.longValue()) : decimal(n.doubleValue());
            };
        }
        return String.valueOf(value);
    }

    private static String decimal(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(CO);
        format.setMinimumFractionDigits(1);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }
}
