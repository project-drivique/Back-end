package com.drivique.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.exceptions.BadPasswordException;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

class ContractEmailProtectionTests {

    private static byte[] samplePdf() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new Paragraph("Contrato"));
        doc.close();
        return out.toByteArray();
    }

    @Test
    void passwordIsTheDocumentNumberWithoutSeparators() {
        assertEquals("1098765432", ContractEmailService.passwordFor(" 1.098.765.432 "));
        assertEquals("AB123456", ContractEmailService.passwordFor("ab-123 456"));
        assertEquals("", ContractEmailService.passwordFor(null));
    }

    @Test
    void protectedPdfOnlyOpensWithTheDocumentNumber() throws Exception {
        byte[] protectedPdf = ContractEmailService.protect(samplePdf(), "1098765432");
        assertThrows(BadPasswordException.class, () -> new PdfReader(protectedPdf));
        assertThrows(BadPasswordException.class, () -> new PdfReader(protectedPdf, "otraclave".getBytes()));
        PdfReader ok = new PdfReader(protectedPdf, "1098765432".getBytes());
        assertEquals(1, ok.getNumberOfPages());
        ok.close();
    }
}
