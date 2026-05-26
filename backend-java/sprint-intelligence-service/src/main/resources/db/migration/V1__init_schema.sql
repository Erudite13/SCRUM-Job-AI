-- Enable pgvector extension for semantic Agile memories
CREATE EXTENSION IF NOT EXISTS pgvector;

-- Sprints Table
CREATE TABLE sprints (
    sprint_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date TIMESTAMP WITH TIME ZONE NOT NULL,
    velocity_forecast DOUBLE PRECISION,
    status VARCHAR(20) DEFAULT 'PLANNING'
);

-- Sync Cache of Azure DevOps Work Items
CREATE TABLE work_items (
    id VARCHAR(50) PRIMARY KEY,
    sprint_id VARCHAR(50) REFERENCES sprints(sprint_id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    assigned_to VARCHAR(100),
    status VARCHAR(30) NOT NULL,
    effort_estimate INTEGER,
    remaining_work INTEGER,
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Semantic Sprint Retro and Blocker Memory (Vector Storage)
CREATE TABLE sprint_memory (
    memory_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sprint_id VARCHAR(50) REFERENCES sprints(sprint_id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    embedding vector(1536), -- Standard OpenAI Ada embedding length
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Semantic Indexing using IVFFlat
CREATE INDEX sprint_memory_idx ON sprint_memory USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
