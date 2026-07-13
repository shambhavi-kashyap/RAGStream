package com.rag.ingestion.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.ingestion.model.DocumentEvent;
import com.rag.ingestion.service.DocumentProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class DocumentIngestionConsumer {

    private final DocumentProcessor documentProcessor;
    private final ObjectMapper objectMapper;

    // Dependency Injection
    public DocumentIngestionConsumer(DocumentProcessor documentProcessor) {
        this.documentProcessor = documentProcessor;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Listens to the 'document-ingestion' topic.
     * The record key is the Tenant ID, ensuring all documents for a single tenant 
     * go to the exact same partition and are processed in order.
     */
    @KafkaListener(topics = "document-ingestion", groupId = "rag-worker-group")
    public void consume(ConsumerRecord<String, String> record) {
        String tenantId = record.key();
        
        System.out.println("📥 Received Kafka Event for Tenant: " + tenantId + " on Partition: " + record.partition());

        try {
            DocumentEvent event = objectMapper.readValue(record.value(), DocumentEvent.class);
            
            // Pass it to the concurrent processor you wrote earlier
            documentProcessor.processDocumentChunks(event.getTenantId(), event.getTextChunks());
            
        } catch (Exception e) {
            System.err.println("❌ Failed to process document event: " + e.getMessage());
            // In a production system, you would route this to a Dead Letter Queue (DLQ) here.
        }
    }
}