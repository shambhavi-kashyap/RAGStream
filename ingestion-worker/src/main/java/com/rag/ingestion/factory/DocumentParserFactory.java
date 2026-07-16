package com.rag.ingestion.factory;

import com.rag.ingestion.parser.DocumentParser;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;

    public DocumentParserFactory(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    public DocumentParser getParser(String contentType) {
        return parsers.stream()
                .filter(parser -> parser.supports(contentType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported document format: " + contentType
                ));
    }
}