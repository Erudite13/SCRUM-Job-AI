import psycopg2
from pgvector.psycopg2 import register_vector
import numpy as np
from typing import List, Dict, Any

class SprintRAGService:
    def __init__(self, db_connection_string: str):
        self.conn_str = db_connection_string
        self._init_db()

    def _init_db(self):
        self.conn = psycopg2.connect(self.conn_str)
        register_vector(self.conn)

    def generate_mock_embedding(self, text: str) -> List[float]:
        """
        Helper method simulating Ada-002 model output length (1536 dimensions)
        """
        np.random.seed(hash(text) % 2**32)
        emb = np.random.randn(1536)
        norm = np.linalg.norm(emb)
        return (emb / norm).tolist()

    def store_sprint_memory(self, sprint_id: str, content: str):
        """
        Inserts new sprint metadata, retro summaries or blockers with embeddings.
        """
        embedding = self.generate_mock_embedding(content)
        with self.conn.cursor() as cur:
            cur.execute(
                "INSERT INTO sprint_memory (sprint_id, content, embedding) VALUES (%s, %s, %s)",
                (sprint_id, content, embedding)
            )
        self.conn.commit()

    def retrieve_similar_contexts(self, query: str, limit: int = 3) -> List[Dict[str, Any]]:
        """
        Performs Cosine Similarity matching against vector embeddings inside PostgreSQL.
        """
        query_vector = self.generate_mock_embedding(query)
        results = []
        with self.conn.cursor() as cur:
            # Cosine distance operator <=> in pgvector
            cur.execute(
                "SELECT sprint_id, content, (embedding <=> %s) AS distance FROM sprint_memory ORDER BY distance ASC LIMIT %s",
                (query_vector, limit)
            )
            for row in cur.fetchall():
                results.append({
                    "sprintId": row[0],
                    "content": row[1],
                    "score": 1 - row[2] # Convert distance to similarity score
                })
        return results

    def close(self):
        self.conn.close()
