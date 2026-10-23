# COTI AI Atendimento

Plataforma de atendimento inteligente para alunos da COTI Informática, formada por dois microsserviços Spring Boot independentes:

- **api-conhecimento** (porta 8081): base de conhecimento, chunking, embeddings e busca vetorial no PostgreSQL/pgvector; expõe a ferramenta MCP `buscar_conhecimento`.
- **api-atendimentos** (porta 8082): recebe perguntas por REST, consulta a base **somente por MCP** e gera a resposta com Spring AI e OpenAI.

A especificação completa está em [`specs/`](specs/) (constituição, requisitos, arquitetura, plano, tarefas e contratos). Agentes de codificação seguem o [`AGENTS.md`](AGENTS.md).

> Estado atual: Fase 1 (infraestrutura do banco) concluída. Os serviços Java ainda não foram criados; acompanhe em [`specs/05-tasks.md`](specs/05-tasks.md).

## Ambiente local

### Pré-requisitos

- Docker Desktop em execução (`docker info` responde sem erro).
- JDK 21 (`java -version`). O Maven vem pelo Maven Wrapper, que será criado na Fase 2.
- Porta `5434` livre no host. Se estiver ocupada, o `docker compose up` falha com "port is already allocated".

### 1. Configurar as variáveis

```bash
cp .env.example .env
```

Preencha no `.env` pelo menos as variáveis do banco:

| Variável | Para quê |
|---|---|
| `POSTGRES_PASSWORD` | Superusuário do container (usado só na criação) |
| `CONHECIMENTO_FLYWAY_USER` / `CONHECIMENTO_FLYWAY_PASSWORD` | Papel dono do schema, usado só pelas migrações (por exemplo `coti_owner`) |
| `CONHECIMENTO_DB_USER` / `CONHECIMENTO_DB_PASSWORD` | Papel da aplicação, só DML (por exemplo `coti_app`) |

Use senhas fortes e diferentes entre si. O `.env` está no `.gitignore` e **nunca** deve ser versionado. As demais variáveis (`OPENAI_API_KEY` etc.) são necessárias a partir da Fase 2.

### 2. Subir o banco

```bash
docker compose up -d postgres
```

```bash
docker compose ps
```

O serviço deve aparecer como `healthy`. O banco fica em `127.0.0.1:5434` (somente loopback, não acessível pela rede local).

### 3. Conferir

Entrando no container com o papel da aplicação:

```bash
docker compose exec postgres sh -c 'PGPASSWORD="$CONHECIMENTO_DB_PASSWORD" psql -h postgres -U "$CONHECIMENTO_DB_USER" -d coti_conhecimento -c "select extname, extversion from pg_extension order by 1"'
```

Devem aparecer `vector`, `hstore` e `uuid-ossp`. Dentro do container, conexões por `127.0.0.1`/socket são confiáveis (padrão da imagem oficial); a verificação de senha vale para conexões que chegam pela rede do Docker e pela porta publicada.

### Parar e recriar

```bash
docker compose down
```

Mantém os dados (volume `coti-pgdata`). Para **apagar tudo** e reexecutar os scripts de `docker/postgres/init/` (que só rodam com volume vazio):

```bash
docker compose down -v
```

### Banco de dados em resumo

| Item | Valor |
|---|---|
| Imagem | `pgvector/pgvector:0.8.7-pg18-trixie` (PostgreSQL 18, pgvector 0.8.7) |
| Banco | `coti_conhecimento` |
| Papel dono | `CONHECIMENTO_FLYWAY_USER`: executa as migrações (DDL) |
| Papel da aplicação | `CONHECIMENTO_DB_USER`: só `SELECT/INSERT/UPDATE/DELETE`; não cria nem altera objetos |
| Volume | `coti-pgdata`, montado em `/var/lib/postgresql` (exigência do PostgreSQL 18) |
