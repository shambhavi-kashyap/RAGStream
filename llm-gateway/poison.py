from confluent_kafka import Producer
import json

producer = Producer({'bootstrap.servers': 'localhost:9092'})

poison_payload = {
    "tenantId": "apple_inc",
    "textChunks": ["TRIGGER_503"]
}

producer.produce(
    topic="document-ingestion", 
    key=b"apple_inc",
    value=json.dumps(poison_payload).encode('utf-8')
)
producer.flush()

print("☠️ Poison pill successfully injected into Kafka topic: document-ingestion")