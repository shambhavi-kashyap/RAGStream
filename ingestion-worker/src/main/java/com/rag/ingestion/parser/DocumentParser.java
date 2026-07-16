package com.rag.ingestion.parser;

public interface DocumentParser {
    boolean supports(String contentType);

    String extractText(byte[] fileContent) throws Exception;
}