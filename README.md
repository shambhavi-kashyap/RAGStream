# RAGStream Enterprise

**A Distributed, Event-Driven, Multi-Tenant Retrieval-Augmented Generation (RAG) Architecture.**

RAGStream is a highly scalable, polyglot microservices platform designed for high-throughput enterprise document ingestion and intelligent semantic querying. It features strict multi-tenant data isolation via JWT, real-time WebSocket observability, dual-tier semantic caching, and an event-driven processing pipeline powered by Apache Kafka.

---

![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-%23231F20.svg?style=for-the-badge&logo=apachekafka&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-%23009688.svg?style=for-the-badge&logo=fastapi&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-%236DB33F.svg?style=for-the-badge&logo=springboot&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-%23DC382D.svg?style=for-the-badge&logo=redis&logoColor=white)
![React](https://img.shields.io/badge/React-%2320232a.svg?style=for-the-badge&logo=react&logoColor=%2361DAFB)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-%23316192.svg?style=for-the-badge&logo=postgresql&logoColor=white)

## System Architecture

```mermaid
flowchart TB
    %% Styling
    classDef frontend fill:#3b82f6,stroke:#2563eb,stroke-width:2px,color:#fff
    classDef python fill:#10b981,stroke:#059669,stroke-width:2px,color:#fff
    classDef java fill:#f59e0b,stroke:#d97706,stroke-width:2px,color:#fff
    classDef kafka fill:#1e40af,stroke:#1e3a8a,stroke-width:2px,color:#fff
    classDef db fill:#6366f1,stroke:#4f46e5,stroke-width:2px,color:#fff
    classDef monitor fill:#f43f5e,stroke:#e11d48,stroke-width:2px,color:#fff

    subgraph FrontendPlane ["🖥️ Frontend Plane"]
        UI["React 18 Dashboard<br>(Vite + Tailwind)"]:::frontend
    end

    subgraph Observability ["📊 Observability"]
        Grafana["Grafana"]:::monitor
        Prometheus(("Prometheus")):::monitor
        Grafana -.->|Visualizes| Prometheus
    end

    subgraph PythonGateway ["🐍 Python API Gateway & AI Engine"]
        FastAPI["FastAPI<br>(JWT Auth)"]:::python
        PyEmbeddings["SentenceTransformers<br>(Local all-MiniLM)"]:::python
        Gemini["Google Gemini 2.5 Flash<br>(LLM Strategy Pattern)"]:::python
    end

    subgraph EventBus ["⚡ Event-Driven Pipeline"]
        Kafka{"Apache Kafka<br>(KRaft Mode)"}:::kafka
        DLQ[("Dead Letter Queue<br>(DLQ)")]:::kafka
    end

    subgraph JavaWorker ["☕ High-Throughput Worker (Java)"]
        SpringBoot["Spring Boot 3<br>(Ingestion Worker)"]:::java
        JavaEmbeddings["LangChain4j<br>(Local all-MiniLM)"]:::java
        Resilience(("Resilience4j<br>Circuit Breaker")):::java
    end

    subgraph DataStorage ["💾 Dual-Tier Data & Storage"]
        Redis[("Redis Stack<br>Tier 1: Semantic Cache")]:::db
        Qdrant[("Qdrant<br>Tier 2: Vector DB")]:::db
        Postgres[("PostgreSQL 16<br>Tenant Metadata")]:::db
    end

    %% Ingestion Flow
    UI == "1. Upload Documents" ==> FastAPI
    FastAPI == "2. Publish Raw Doc Event" ==> Kafka
    Kafka == "3. Consume Stream" ==> SpringBoot
    SpringBoot <-->|"Chunk & Embed"| JavaEmbeddings
    SpringBoot == "4. Store Vectors" ==> Qdrant
    SpringBoot == "5. Save Metadata" ==> Postgres
    SpringBoot -.->|"Route on Failure"| Resilience
    Resilience -.->|"Fallback"| DLQ
    SpringBoot -.->|"6. Real-time Progress (SockJS/STOMP)"| UI

    %% Query Flow
    UI -- "A. Semantic Query" --> FastAPI
    FastAPI <-->|"Generate Embeddings"| PyEmbeddings
    FastAPI -- "B. Check Cache (Sub-ms HNSW)" --> Redis
    FastAPI -- "C. Vector Search (Cache Miss)" --> Qdrant
    FastAPI -- "D. Augment Context & Generate" --> Gemini

    %% Metrics Flow
    Prometheus -.->|"Scrape /metrics"| FastAPI
    Prometheus -.->|"Scrape /actuator/prometheus"| SpringBoot
```

## Core Architecture & Features

* **Strict Multi-Tenancy:** Secure data isolation using stateless JWTs and Spring Security. The `tenant_id` is encoded directly into custom token claims, ensuring organizations can only query and ingest data belonging to their specific vector namespaces.
* **Event-Driven Ingestion (Kafka):** Heavy document processing is entirely decoupled from the API layer. Raw PDFs are shipped via FastAPI to an Apache Kafka (KRaft) cluster. Java workers consume these streams, chunk the data, and publish back to Kafka for parallel processing.
* **Tiered RAG Architecture:**
    * **Tier 1 (Redis Stack):** Sub-millisecond semantic caching intercepting incoming vector queries to execute HNSW cosine-similarity matches, bypassing expensive LLM generation for recurrent requests.
    * **Tier 2 (Qdrant):** High-dimensional vector search isolated by tenant payloads.
* **Local AI Embeddings:** Both the Python Gateway and Java Workers utilize local `all-MiniLM-L6-v2` models (via `SentenceTransformers` and `LangChain4j`) to generate vector embeddings, resulting in zero API costs and zero network latency for data ingestion.
* **Enterprise Resilience:** 
    * **Circuit Breakers:** `Resilience4j` protects the Java Kafka publishers, routing to Dead Letter Queues (DLQ) if the broker is unreachable.
    * **LLM Strategy Pattern:** Python Gateway features automatic fallbacks. If the primary LLM (Google Gemini 2.5 Flash) fails, the system degrades gracefully to return raw vector database context.

* **Extensible Ingestion Pipeline:** Document parsing is built using the **Factory and Strategy Design Patterns**. The `DocumentParserFactory` dynamically routes incoming payloads (e.g., PDF, TXT) to the correct parsing strategy at runtime, ensuring the ingestion engine is highly modular and adheres strictly to the Open/Closed Principle.

* **Full-Stack Observability:** 
    * **Real-Time WebSockets:** Spring Boot `SimpMessagingTemplate` streams ingestion progress chunk-by-chunk to the React UI via SockJS/STOMP.
    * **Metrics:** Prometheus and Grafana continuously scrape data from Spring Boot Actuator and FastAPI Instrumentator.

---

## Platform Highlights

### Strict Multi-Tenancy
Secure data isolation using stateless JWTs and Spring Security. The `tenant_id` is encoded directly into custom token claims.

![Tenant Ingestion Form](./assets/ingestion-form.png)

### Event-Driven Ingestion
Spring Boot `SimpMessagingTemplate` streams Kafka ingestion progress chunk-by-chunk to the React UI via SockJS/STOMP.

![Successful Pipeline](./assets/success-pipeline.png)

### Secure Authentication
The entire platform is gated behind stateless JWT authentication backed by Spring Security, ensuring that all REST APIs and real-time WebSocket streams are strictly protected. 

![Authentication Screen](./assets/login.png)

### Enterprise Resilience & Graceful Degradation
The Python API Gateway implements the Strategy Pattern for LLM generation. If the primary LLM (Google Gemini 2.5 Flash) experiences an outage or rate limit, the system gracefully degrades to return the raw, un-augmented vector database context—ensuring zero downtime for end users.

![Graceful Fallback Mechanism](./assets/fallback-resilience.png)

### Full-Stack Observability
System health and ingestion throughput are continuously monitored using Prometheus and visualized in real-time via Grafana.

| Ingestion Worker CPU Load | API Request Rate |
| :---: | :---: |
| ![CPU Load](./assets/cpu-load.png) | ![API Rate](./assets/api-rate.png) |

## Tech Stack

**Frontend Plane**
* React 18 + TypeScript + Vite
* Tailwind CSS
* Axios (with JWT Interceptors)
* SockJS & StompJS

**API Gateway & AI Engine (Python)**
* Python 3.11 / FastAPI
* Google GenAI SDK (Gemini 2.5 Flash)
* SentenceTransformers (Local Embeddings)

**High-Throughput Worker (Java)**
* Java 21 / Spring Boot 3
* Spring Security & JJWT
* Spring Kafka & LangChain4j
* Resilience4j (Circuit Breakers)
* Flyway (Database Migrations)

**Infrastructure & DevOps**
* Apache Kafka (KRaft mode)
* Qdrant (Vector Database)
* Redis Stack (Semantic Cache)
* PostgreSQL 16
* Prometheus & Grafana
* Kubernetes (Deployment, ConfigMaps, Secrets) & Testcontainers

---

## Getting Started

### Prerequisites
Ensure you have the following installed on your local machine:
* Docker & Docker Compose
* Java 21 & Maven
* Python 3.11+
* Node.js 18+

### 1. Start the Infrastructure & Observability Stack
Spin up Kafka, Redis, Qdrant, PostgreSQL, Prometheus, and Grafana:
```bash
docker-compose up -d
```
### 2. Start the AI Gateway (Python)
Navigate to the gateway directory, install dependencies, and start the FastAPI server:

```bash
cd llm-gateway
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
```

Create a local environment file named `.env` inside the `llm-gateway` folder (this file should stay local and is not committed to GitHub):

```env
GEMINI_API_KEY=your_api_key_here
```

> Contributors should create their own `.env` file locally and paste their Gemini API key there before running the gateway.

Then, set a local JWT secret in your shell:

#### Windows PowerShell
```powershell
$env:JWT_SECRET="your-very-long-random-secret-here"
```

#### Linux/macOS
```bash
export JWT_SECRET="your-very-long-random-secret-here"
```

Then start the application:

```bash
uvicorn main:app --reload --port 8000
```

### 3. Start the Ingestion Worker (Java)
Navigate to the worker directory and run the Spring Boot application. Flyway will automatically migrate the PostgreSQL database on startup.

```bash
cd ingestion-worker
./mvnw spring-boot:run
```

### 4. Start the Frontend UI (React)
Navigate to the frontend directory, install packages, and start the development server:

```bash
cd admin-dashboard
npm install
npm run dev
```

Access the multi-tenant RAG dashboard at http://localhost:5173.

## Testing
This project utilizes Testcontainers for true integration testing against actual Docker instances of Kafka and PostgreSQL.

To run the Java integration tests:

```bash
cd ingestion-worker
./mvnw test
``` 
