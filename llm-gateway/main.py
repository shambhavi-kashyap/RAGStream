from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
import redis
import json
import time

app = FastAPI(title="LLM Gateway & Semantic Cache", version="1.0")

# 1. Initialize the local embedding model
print("Loading embedding model...")
model = SentenceTransformer('all-MiniLM-L6-v2')

# 2. Connect to Redis (Running via your Docker Compose)
cache = redis.Redis(host='localhost', port=6379, decode_responses=True)

class ChunkRequest(BaseModel):
    tenant_id: str
    document_id: str
    text_chunk: str

class QueryRequest(BaseModel):
    tenant_id: str
    user_query: str

@app.post("/api/v1/embed")
async def generate_embedding(request: ChunkRequest):
    """Used by the Java worker to generate vectors for document chunks."""
    vector = model.encode(request.text_chunk).tolist()
    return {"dimensions": len(vector), "vector": vector}

@app.post("/api/v1/ask")
async def ask_rag_system(request: QueryRequest):
    """The main search endpoint used by the React frontend."""
    start_time = time.time()
    
    # --- TIER 1: EXACT CACHE MATCH ---
    # If someone asked this exact question recently, return it instantly (0 LLM cost)
    cache_key = f"cache:{request.tenant_id}:{request.user_query.lower().strip()}"
    cached_answer = cache.get(cache_key)
    
    if cached_answer:
        return {
            "source": "redis_exact_cache",
            "latency_ms": round((time.time() - start_time) * 1000, 2),
            "answer": cached_answer
        }
        
    # --- TIER 2: SEMANTIC GENERATION (Cache Miss) ---
    # 1. Convert the user's text query into a vector
    query_vector = model.encode(request.user_query).tolist()
    
    # 2. (In a full app, you would query Qdrant here using this vector to find documents)
    # 3. (Then you would send those documents to an LLM like OpenAI to generate an answer)
    
    # For now, we simulate the expensive LLM generating an answer
    simulated_llm_answer = f"This is a generated answer from the LLM based on internal documents regarding: '{request.user_query}'"
    
    # 4. Save this new answer to Redis so the next person asking gets the cached version!
    # Set it to expire after 24 hours (86400 seconds)
    cache.setex(cache_key, 86400, simulated_llm_answer)
    
    return {
        "source": "expensive_llm_generation",
        "latency_ms": round((time.time() - start_time) * 1000, 2),
        "answer": simulated_llm_answer
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)