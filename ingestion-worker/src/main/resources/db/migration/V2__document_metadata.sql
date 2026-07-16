CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    processing_status VARCHAR(30) NOT NULL DEFAULT 'IDLE',
    total_chunks INT DEFAULT 0,
    file_size_bytes BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_documents_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT
);

CREATE INDEX idx_documents_tenant_status ON documents(tenant_id, processing_status);