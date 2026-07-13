from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

app = FastAPI(title="LLM Gateway", version="1.0")

# Load a lightweight, fast embedding model (runs locally, no OpenAI key needed yet)
model = SentenceTransformer('all-MiniLM-L6-v2')

class ChunkRequest(BaseModel):
    tenant_id: str
    document_id: str
    text_chunk: str

class EmbeddingResponse(BaseModel):
    dimensions: int
    vector: list[float]

@app.post("/api/v1/embed", response_model=EmbeddingResponse)
async def generate_embedding(request: ChunkRequest):
    try:
        # Generate the vector embedding for the text chunk
        vector = model.encode(request.text_chunk).tolist()
        return EmbeddingResponse(dimensions=len(vector), vector=vector)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)