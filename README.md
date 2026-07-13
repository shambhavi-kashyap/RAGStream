# RAGStream – Distributed Event-Driven Ingestion Platform

An enterprise-grade, multi-tenant Retrieval-Augmented Generation (RAG) platform designed for high-throughput document ingestion and semantic search. 

This project utilizes a **Polyglot Microservices Architecture** to leverage the best tools for specific workloads: heavy data streaming in Java, AI/ML embedding generation in Python, and a responsive dashboard in React.

## 🏗️ Architecture Stack

* **Ingress & Gateway (Python 3.11 + FastAPI):** Normalizes LLM payloads (Adapter pattern) and generates vector embeddings via local models.
* **Ingestion Worker (Java 21 + Spring Boot):** Consumes document chunks, utilizing `ExecutorService` and `CompletableFuture` for highly parallelized processing, while safely managing state via `ConcurrentHashMap`.
* **Event Broker (Apache Kafka):** Handles high-throughput document ingestion, partitioning streams by Tenant ID to ensure sequential ordering across a multi-worker cluster.
* **Semantic Cache (Redis):** Intercepts incoming vector queries to execute cosine-similarity matches, bypassing expensive LLM generation for recurrent requests.
* **Frontend Plane (React + TypeScript):** Multi-tenant dashboard for uploading corpora and tracking real-time pipeline execution.
* **Vector Storage (Qdrant):** Stores high-dimensional embeddings isolated by tenant namespaces.

## 🚀 Getting Started

Ensure you have Docker and Docker Compose installed.

1. Start the infrastructure (Kafka, Redis, Qdrant):
   ```bash
   docker-compose up -d
   ```

2. Start the AI Gateway (Port 8000)

3. Start the Java Ingestion Worker (Port 8080)

4. Start the React UI (Port 5173)