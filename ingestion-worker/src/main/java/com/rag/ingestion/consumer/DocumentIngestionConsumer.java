package com.rag.ingestion.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.ingestion.service.DocumentProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class DocumentIngestionConsumer {

    private final DocumentProcessor documentProcessor;
    private final ObjectMapper objectMapper;

    public DocumentIngestionConsumer(DocumentProcessor documentProcessor) {
        this.documentProcessor = documentProcessor;
        this.objectMapper = new ObjectMapper();
    }

    @KafkaListener(topics = "raw-document-ingestion", groupId = "rag-worker-group")
    public void consumeRawFile(ConsumerRecord<String, String> record) throws Exception {
        System.out.println("📥 [KAFKA] Received RAW FILE payload from Python Gateway!");
        
        JsonNode json = objectMapper.readTree(record.value());
        String tenantId = json.get("tenant_id").asText();
        String contentType = json.get("content_type").asText();
        
        byte[] rawFileBytes = java.util.Base64.getDecoder().decode(json.get("file_data_base64").asText());

        documentProcessor.processIncomingFile(tenantId, rawFileBytes, contentType);
    }
}