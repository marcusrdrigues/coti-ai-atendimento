-- Tabela do PgVectorStore (Spring AI 2.0), com a estrutura que ele espera: id, content, metadata, embedding.
-- A dimensão (1536) é a do modelo text-embedding-3-small. Trocar de modelo ou de dimensão exige uma nova
-- migração e a reindexação completa da base (RN03.3). O esquema NÃO é criado pela aplicação
-- (spring.ai.vectorstore.pgvector.initialize-schema=false).
CREATE TABLE vector_store (
    id        uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(1536)
);

-- Busca por similaridade de cosseno (distance-type COSINE_DISTANCE) com índice HNSW.
CREATE INDEX vector_store_embedding_hnsw_idx ON vector_store USING hnsw (embedding vector_cosine_ops);

-- Apagar ou publicar todos os trechos de um documento filtra por metadata->>'documentoId'.
CREATE INDEX vector_store_documento_id_idx ON vector_store ((metadata ->> 'documentoId'));
