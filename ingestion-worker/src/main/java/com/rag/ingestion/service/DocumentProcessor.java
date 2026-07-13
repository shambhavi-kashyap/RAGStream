package com.rag.ingestion.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.concurrent.*;

@Service
public class DocumentProcessor {

    // 1. Thread Pool: Bounded to prevent OutOfMemory errors on massive documents
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    // 2. Safe State Tracking: Thread-safe map to track progress per tenant
    private final ConcurrentHashMap<String, Integer> ingestionState = new ConcurrentHashMap<>();
    
    private final RestTemplate restTemplate = new RestTemplate(); // To call Python API

    public void processDocumentChunks(String tenantId, List<String> textChunks) {
        // Initialize state
        ingestionState.put(tenantId, 0);

        // 3. CompletableFuture: Parallelize calls to the Python API
        List<CompletableFuture<Void>> futures = textChunks.stream()
            .map(chunk -> CompletableFuture.runAsync(() -> {
                
                // Call Python FastAPI to get embeddings
                callPythonGatewayForEmbedding(tenantId, chunk);
                
                // Safely update state across multiple threads
                ingestionState.computeIfPresent(tenantId, (key, val) -> val + 1);
                
            }, executorService))
            .toList();

        // Wait for all parallel chunks to finish
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        System.out.println("Finished processing " + ingestionState.get(tenantId) + " chunks for tenant: " + tenantId);
    }

    private void callPythonGatewayForEmbedding(String tenantId, String chunk) {
        // Here you will make an HTTP POST request to http://localhost:8000/api/v1/embed
        // and push the resulting vector + metadata to your Vector DB.
        System.out.println("Thread " + Thread.currentThread().getName() + " embedding chunk.");
    }
}