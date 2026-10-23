#!/bin/bash
# Executado pelo entrypoint da imagem postgres apenas na primeira inicialização (volume vazio).
# Cria o banco da api-conhecimento e dois papéis (specs/03-architecture.md §4.1):
#   - dono (CONHECIMENTO_FLYWAY_USER): executa as migrações (DDL); usado só pelo Flyway.
#   - aplicação (CONHECIMENTO_DB_USER): somente DML; não cria nem altera objetos.
# As senhas vêm do ambiente do container; nada é gravado em arquivo versionado.
set -euo pipefail

: "${CONHECIMENTO_FLYWAY_USER:?defina CONHECIMENTO_FLYWAY_USER}"
: "${CONHECIMENTO_FLYWAY_PASSWORD:?defina CONHECIMENTO_FLYWAY_PASSWORD}"
: "${CONHECIMENTO_DB_USER:?defina CONHECIMENTO_DB_USER}"
: "${CONHECIMENTO_DB_PASSWORD:?defina CONHECIMENTO_DB_PASSWORD}"

BANCO="${CONHECIMENTO_DB_NAME:-coti_conhecimento}"

if [ "$CONHECIMENTO_FLYWAY_USER" = "$CONHECIMENTO_DB_USER" ]; then
  echo "ERRO: o papel das migrações e o da aplicação devem ser diferentes." >&2
  exit 1
fi

# Variáveis do psql (-v) evitam concatenar valores em SQL: :"x" vira identificador, :'x' vira literal.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v dono="$CONHECIMENTO_FLYWAY_USER" -v donosenha="$CONHECIMENTO_FLYWAY_PASSWORD" \
  -v app="$CONHECIMENTO_DB_USER" -v appsenha="$CONHECIMENTO_DB_PASSWORD" \
  -v banco="$BANCO" <<'SQL'
CREATE ROLE :"dono" LOGIN PASSWORD :'donosenha' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE :"app"  LOGIN PASSWORD :'appsenha'  NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE :"banco" OWNER :"dono";
REVOKE ALL ON DATABASE :"banco" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"banco" TO :"dono", :"app";
-- Os papéis da aplicação não precisam entrar nos bancos padrão do servidor.
REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;
REVOKE CONNECT ON DATABASE template1 FROM PUBLIC;
SQL

# Extensões exigidas pelo PgVectorStore são criadas aqui, como superusuário, para que o
# Flyway (papel sem superusuário) apenas encontre as extensões já instaladas.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$BANCO" \
  -v dono="$CONHECIMENTO_FLYWAY_USER" -v app="$CONHECIMENTO_DB_USER" <<'SQL'
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Esquema public: o dono cria objetos; a aplicação só usa. No PostgreSQL 15+ o PUBLIC não cria no public.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO :"dono";
GRANT USAGE ON SCHEMA public TO :"app";

-- Tudo o que o dono criar no futuro (migrações) fica acessível, só para DML, à aplicação.
ALTER DEFAULT PRIVILEGES FOR ROLE :"dono" IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"dono" IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO :"app";
SQL

echo "Init concluído: banco '$BANCO', papéis de migração e de aplicação criados."
