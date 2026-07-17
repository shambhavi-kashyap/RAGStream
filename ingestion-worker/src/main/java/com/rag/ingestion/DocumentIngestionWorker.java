package com.rag.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DocumentIngestionWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionWorker.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final SimpMessagingTemplate messagingTemplate;

    private final ConcurrentHashMap<String, AtomicInteger> chunksProcessedTracker = new ConcurrentHashMap<>();

    public DocumentIngestionWorker(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topics = "rag-document-chunks", groupId = "rag-vector-workers")
    public void consumeDocumentChunk(String messagePayload) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonNode event = objectMapper.readTree(messagePayload);
                String tenantId = event.get("tenant_id").asText();
                String textPayload = event.get("text_payload").asText();
                int totalChunks = event.get("total_chunks").asInt();

                Embedding vector = embeddingModel.embed(textPayload).content();

                upsertToQdrant(tenantId, textPayload, vector.vectorAsList());
                
                chunksProcessedTracker.putIfAbsent(tenantId, new AtomicInteger(0));
                
                int currentFinishedCount = chunksProcessedTracker.get(tenantId).incrementAndGet();
                
                double progress = ((double) currentFinishedCount / totalChunks) * 100;
                String wsPayload = String.format("{\"tenant\":\"%s\", \"progress\":%.0f}", tenantId, progress);
                
                messagingTemplate.convertAndSend("/topic/progress/" + tenantId, wsPayload);
                
                log.info("✅ SUCCESS: Indexed chunk {}/{} ({}%) for tenant [{}]", 
                         currentFinishedCount, totalChunks, (int)progress, tenantId);

                if (currentFinishedCount >= totalChunks) {
                    chunksProcessedTracker.remove(tenantId);
                    log.info("🎉 Tenant [{}] ingestion fully complete. State cleared from memory.", tenantId);
                }

            } catch (Exception e) {
                log.error("❌ Failed to process Kafka chunk: {}", e.getMessage());
            }
        }, executorService);
    }

    private void upsertToQdrant(String tenantId, String textContent, java.util.List<Float> vector) throws Exception {
        String pointId = UUID.randomUUID().toString();
        java.util.Map<String, Object> payloadMap = java.util.Map.of("tenant_id", tenantId, "text_content", textContent);
        java.util.Map<String, Object> pointMap = java.util.Map.of("id", pointId, "vector", vector, "payload", payloadMap);
        java.util.Map<String, Object> rootMap = java.util.Map.of("points", java.util.List.of(pointMap));

        String jsonBody = objectMapper.writeValueAsString(rootMap);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:6333/collections/ragstream_documents/points"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Qdrant rejected the upsert: " + response.body());
        }
    }
}