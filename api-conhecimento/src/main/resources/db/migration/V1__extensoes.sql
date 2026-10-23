-- Extensões exigidas pelo PgVectorStore (Spring AI 2.0). Em produção e no Compose elas já são criadas pelo
-- script de inicialização do banco, como superusuário (docker/postgres/init/01-roles.sh); o papel das
-- migrações não é superusuário. Com IF NOT EXISTS, esta migração não faz nada quando já existem.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
