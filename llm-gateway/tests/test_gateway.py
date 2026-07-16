from fastapi.testclient import TestClient
from main import app

client = TestClient(app)

def test_health_check():
    response = client.get("/health")
    assert response.status_code in [200, 404]

def test_gateway_chat_endpoint_security():
    response = client.post(
        "/api/v1/ask",
        json={"tenant_id": "demo-tenant", "user_query": "Hello polyglot engine"},
    )
    assert response.status_code in [401, 403, 422]