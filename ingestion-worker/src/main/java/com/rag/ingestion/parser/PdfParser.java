package com.rag.ingestion.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfParser implements DocumentParser {

    @Override
    public boolean supports(String contentType) {
        return "application/pdf".equalsIgnoreCase(contentType) || "pdf".equalsIgnoreCase(contentType);
    }

    @Override
    public String extractText(byte[] fileContent) throws Exception {
        try (PDDocument document = PDDocument.load(fileContent)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}