import io
import json
import time
import os
import numpy as np
import base64
from prometheus_fastapi_instrumentator import Instrumentator
from fastapi import FastAPI, HTTPException, File, UploadFile, Depends, Form
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
from confluent_kafka import Producer

from abc import ABC, abstractmethod 

from google import genai
from google.genai import types
from dotenv import load_dotenv

load_dotenv(dotenv_path=os.path.join(os.path.dirname(__file__), ".env"))

api_key = os.getenv("GEMINI_API_KEY")
if not api_key:
    raise ValueError("GEMINI_API_KEY environment variable is missing! Add it to the .env file or export it in your shell.")

gemini_client = genai.Client(api_key=api_key)

import redis
from redis.commands.search.field import VectorField, TextField
from redis.commands.search.query import Query
try:
    from redis.commands.search.indexDefinition import IndexDefinition, IndexType
except ModuleNotFoundError:
    try:
        from redis.commands.search.index_definition import IndexDefinition, IndexType
    except ModuleNotFoundError:
        from redis.commands.search import IndexDefinition, IndexType

from qdrant_client import QdrantClient
from qdrant_client.models import Distance, VectorParams, PointStruct, Filter, FieldCondition, MatchValue

app = FastAPI(title="RAGStream Core AI Engine", version="2.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], 
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

Instrumentator().instrument(app).expose(app)

MODEL_NAME = 'all-MiniLM-L6-v2'
VECTOR_DIM = 384
print(f"Initializing embedding model: {MODEL_NAME}...")
model = SentenceTransformer(MODEL_NAME)

qdrant_client = QdrantClient(host="localhost", port=6333)
redis_client = redis.Redis(host="localhost", port=6379, decode_responses=False)

REDIS_INDEX_NAME = "idx:semantic_cache"
QDRANT_COLLECTION = "ragstream_documents"
SEMANTIC_THRESHOLD = 0.85 

kafka_config = {
    'bootstrap.servers': 'localhost:9092',
    'client.id': 'fastapi-rag-producer',
    'message.max.bytes': 10485760, 
    'acks': 'all' 
}
try:
    kafka_producer = Producer(kafka_config)
    print("🟢 Kafka Producer successfully connected to broker.")
except Exception as e:
    print(f"⚠️ Kafka Producer failed to initialize: {e}")

class IngestionRequest(BaseModel):
    tenant_id: str = None 
    document_id: str
    text_chunks: list[str]

class QueryRequest(BaseModel):
    tenant_id: str = None 
    user_query: str

class LLMProvider(ABC):
    @abstractmethod
    def generate(self, context: str, user_query: str) -> str:
        pass

class GeminiProvider(LLMProvider):
    def __init__(self, client):
        self.client = client
        
    def generate(self, context: str, user_query: str) -> str:
        system_instructions = """
        You are a highly precise enterprise AI assistant. 
        Read the following context, and answer the user's query directly and concisely. 
        Do NOT repeat the surrounding context.
        """
        user_prompt = f"Context: {context}\n\nUser Query: {user_query}"
        
        response = self.client.models.generate_content(
            model="gemini-2.5-flash",
            contents=user_prompt,
            config=types.GenerateContentConfig(
                system_instruction=system_instructions,
                temperature=0.1
            )
        )
        return response.text

class FallbackProvider(LLMProvider):
    def generate(self, context: str, user_query: str) -> str:
        return f"⚠️ **Gemini API is currently unreachable. Displaying raw database results:**\n\n{context}"

@app.on_event("startup")
def initialize_vector_infrastructure():
    try:
        if not qdrant_client.collection_exists(QDRANT_COLLECTION):
            qdrant_client.create_collection(
                collection_name=QDRANT_COLLECTION,
                vectors_config=VectorParams(size=VECTOR_DIM, distance=Distance.COSINE)
            )
            print(f"🟢 Qdrant Collection '{QDRANT_COLLECTION}' successfully created.")
    except Exception as e:
        print(f"⚠️ Qdrant initialization skipped or failed: {e}")

    try:
        redis_client.ft(REDIS_INDEX_NAME).info()
        print(f"🟢 Redis Vector Index '{REDIS_INDEX_NAME}' already exists.")
    except Exception:
        schema = (
            TextField("text_query"),
            TextField("cached_answer"),
            TextField("tenant_id"),
            VectorField("query_vector", "HNSW", {
                "TYPE": "FLOAT32",
                "DIM": VECTOR_DIM,
                "DISTANCE_METRIC": "COSINE",
                "INITIAL_CAP": 1000
            })
        )
        redis_client.ft(REDIS_INDEX_NAME).create_index(
            fields=schema,
            definition=IndexDefinition(prefix=["samc:"], index_type=IndexType.HASH)
        )
        print(f"🟢 Redis Vector Index '{REDIS_INDEX_NAME}' initialized successfully via HNSW.")

@app.post("/api/v1/ask")
async def process_rag_query(request: QueryRequest):
    secure_tenant_id = request.tenant_id
    
    start_time = time.time()
    query_vector = model.encode(request.user_query).astype(np.float32).tobytes()
    
    redis_query = (
        Query(f"(@tenant_id:{secure_tenant_id})=>[KNN 1 @query_vector $vec AS score]")
        .sort_by("score")
        .return_fields("text_query", "cached_answer", "score")
        .dialect(2)
    )
    
    try:
        cache_results = redis_client.ft(REDIS_INDEX_NAME).search(redis_query, query_params={"vec": query_vector})
        if cache_results.docs:
            closest_match = cache_results.docs[0]
            similarity_score = 1.0 - float(closest_match.score)
            
            if similarity_score >= SEMANTIC_THRESHOLD:
                return {
                    "source": "redis_semantic_cache",
                    "similarity": round(similarity_score, 4),
                    "latency_ms": round((time.time() - start_time) * 1000, 2),
                    "matched_cached_query": closest_match.text_query,
                    "answer": closest_match.cached_answer
                }
    except Exception as e:
        print(f"⚠️ Redis Cache search error: {e}")

    try:
        raw_vector = model.encode(request.user_query).tolist()
        
        tenant_filter = Filter(
            must=[FieldCondition(key="tenant_id", match=MatchValue(value=secure_tenant_id))]
        )
        
        search_results = qdrant_client.query_points(
            collection_name=QDRANT_COLLECTION,
            query=raw_vector,
            query_filter=tenant_filter,
            limit=3
        ).points
        
        if not search_results:
            return {"source": "llm_fallback", "answer": "No relevant documentation found for your organization."}
            
        context_blocks = list(dict.fromkeys([res.payload["text_content"] for res in search_results]))
        unified_context = "\n---\n".join(context_blocks)
        
        llm_strategy = GeminiProvider(gemini_client)
        fallback_strategy = FallbackProvider()
        
        generated_answer = ""
        try:
            print("🧠 Attempting Primary Gemini AI Generation...")
            generated_answer = llm_strategy.generate(unified_context, request.user_query)
        except Exception as e:
            print(f"⚠️ Gemini failed ({e}). Switching to Fallback Strategy!")
            generated_answer = fallback_strategy.generate(unified_context, request.user_query)
        
        cache_id = f"samc:{secure_tenant_id}:{hash(request.user_query) & 0xFFFFFFFF}"
        redis_client.hset(cache_id, mapping={
            "text_query": request.user_query,
            "cached_answer": generated_answer,
            "tenant_id": secure_tenant_id,
            "query_vector": query_vector
        })
        redis_client.expire(cache_id, 86400)
        
        return {
            "source": "qdrant_vector_search_and_llm",
            "latency_ms": round((time.time() - start_time) * 1000, 2),
            "retrieved_chunks_count": len(search_results),
            "answer": generated_answer
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Critical Pipeline Execution Failure: {str(e)}")

@app.post("/api/v1/upload-pdf")
async def upload_and_queue_document(
    file: UploadFile = File(...),
    tenant_id: str = Form(...) 
):
    try:
        file_bytes = await file.read()
        base64_file = base64.b64encode(file_bytes).decode('utf-8')

        event_payload = {
            "tenant_id": tenant_id,
            "file_name": file.filename,
            "content_type": "application/pdf",
            "file_data_base64": base64_file
        }
        
        kafka_producer.produce(
            topic="raw-document-ingestion",
            key=tenant_id.encode('utf-8'),
            value=json.dumps(event_payload).encode('utf-8')
        )
        kafka_producer.flush()

        return {"status": "ACCEPTED", "message": "Raw file shipped to Java Factory!"}

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to queue file: {str(e)}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)