package com.rag.ingestion.service;

import com.rag.ingestion.factory.DocumentParserFactory;
import com.rag.ingestion.parser.DocumentParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class DocumentProcessor {

    private final DocumentParserFactory parserFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DocumentProcessor(DocumentParserFactory parserFactory, KafkaTemplate<String, String> kafkaTemplate) {
        this.parserFactory = parserFactory;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public void processIncomingFile(String tenantId, byte[] rawFile, String contentType) {
        try {
            System.out.println("📄 [PROCESSOR] Received file for tenant: " + tenantId + " | Type: " + contentType);
            
            DocumentParser parser = parserFactory.getParser(contentType);
            String fullText = parser.extractText(rawFile);
            System.out.println("✅ Successfully extracted text for tenant: " + tenantId);
            
            List<String> textChunks = Arrays.asList(fullText.split("\\n\\n"));
            
            publishChunksToKafka(tenantId, textChunks);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse document: " + e.getMessage(), e);
        }
    }

    @CircuitBreaker(name = "kafkaPublishCall", fallbackMethod = "fallbackPublish")
    public void publishChunksToKafka(String tenantId, List<String> textChunks) throws Exception {
        System.out.println("🚀 [PROCESSOR] Publishing " + textChunks.size() + " chunks to worker queue...");
        
        for (int i = 0; i < textChunks.size(); i++) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("tenant_id", tenantId);
            payload.put("text_payload", textChunks.get(i));
            payload.put("chunk_index", i);
            payload.put("total_chunks", textChunks.size());

            kafkaTemplate.send("rag-document-chunks", tenantId, payload.toString());
        }
        System.out.println("✅ All chunks dispatched! Handing off to AI Embedding Workers.");
    }

    public void fallbackPublish(String tenantId, List<String> textChunks, Exception ex) {
        System.err.println("🛑 [CIRCUIT BREAKER] Kafka is unreachable. Routing to DLQ...");
        throw new RuntimeException("Kafka Publish Failed", ex);
    }
}