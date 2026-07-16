package com.rag.ingestion.parser;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

@Component
public class TxtParser implements DocumentParser {

    @Override
    public boolean supports(String contentType) {
        return "text/plain".equalsIgnoreCase(contentType) || "txt".equalsIgnoreCase(contentType);
    }

    @Override
    public String extractText(byte[] fileContent) {
        return new String(fileContent, StandardCharsets.UTF_8);
    }
}