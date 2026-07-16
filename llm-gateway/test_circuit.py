from confluent_kafka import Producer
import json
import time

producer = Producer({'bootstrap.servers': 'localhost:9092'})

print("🔫 Firing 5 network-crashing messages into Kafka...")

for i in range(1, 6):
    payload = {
        "tenantId": "apple_inc",
        "textChunks": [f"Test Document {i}", "TRIGGER_503"]
    }
    producer.produce(
        topic="document-ingestion", 
        key=b"apple_inc",
        value=json.dumps(payload).encode('utf-8')
    )
    time.sleep(0.1)

producer.flush()
print("☠️ Payload delivered. Go look at your Java logs!")