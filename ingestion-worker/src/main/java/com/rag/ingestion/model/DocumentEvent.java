package com.rag.ingestion.model;

import lombok.Data;
import java.util.List;

@Data
public class DocumentEvent {
    private String tenantId;
    private String documentId;
    private List<String> textChunks;
}