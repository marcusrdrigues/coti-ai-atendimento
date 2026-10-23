# COTI AI Atendimento

Plataforma de atendimento inteligente para alunos da COTI Informática, formada por dois microsserviços Spring Boot independentes:

- **api-conhecimento** (porta 8081): base de conhecimento, chunking, embeddings e busca vetorial no PostgreSQL/pgvector; expõe a ferramenta MCP `buscar_conhecimento`.
- **api-atendimentos** (porta 8082): recebe perguntas por REST, consulta a base **somente por MCP** e gera a resposta com Spring AI e OpenAI.

A especificação completa está em [`specs/`](specs/) (constituição, requisitos, arquitetura, plano, tarefas e contratos). Agentes de codificação seguem o [`AGENTS.md`](AGENTS.md).

> Estado atual: Fases 1 e 2 concluídas (banco em Docker e os dois serviços inicializando com segurança, erros padronizados, Actuator e OpenAPI). Endpoints de negócio começam na Fase 3; acompanhe em [`specs/05-tasks.md`](specs/05-tasks.md).

## Ambiente local

### Pré-requisitos

- Docker Desktop em execução (`docker info` responde sem erro).
- JDK 21 (`java -version`). O Maven vem pelo Maven Wrapper (`./mvnw`).
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

Use senhas fortes e diferentes entre si. O `.env` está no `.gitignore` e **nunca** deve ser versionado.

Para subir as APIs (Fase 2) preencha também:

| Variável | Para quê |
|---|---|
| `OPENAI_API_KEY` | Chave da OpenAI (as APIs não chamam a OpenAI antes das Fases 4 e 7, mas a subida exige a variável) |
| `OPENAI_CHAT_MODEL` | Identificador do modelo de chat, com versão (`api-atendimentos`); será definido pelo eval (T070) |
| `CONHECIMENTO_ADMIN_API_KEY` | Chave administrativa (`X-Api-Key`) da `api-conhecimento` |
| `MONITORAMENTO_API_KEY` | Chave (`X-Api-Key`) para métricas e detalhes do health |

Variável definida no shell tem precedência sobre o `.env` no Docker Compose; confira com `echo` se não quiser que uma chave do seu ambiente seja repassada aos contêineres.

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

## Build, testes e execução das APIs

### Compilar e testar (não precisa de Docker nem de internet depois do primeiro download)

```bash
./mvnw verify
```

Um serviço apenas:

```bash
./mvnw -pl api-conhecimento verify
```

### Subir tudo com Docker

```bash
docker compose up -d --build
```

```bash
docker compose ps
```

| Serviço | Endereço | Observação |
|---|---|---|
| `api-conhecimento` | `http://localhost:8081` | `GET /actuator/health` público; o resto exige chave |
| `api-atendimentos` | `http://localhost:8082` | `POST /api/atendimentos/perguntar` é público (a partir da Fase 6) |
| PostgreSQL | `localhost:5434` | somente loopback |

A `api-atendimentos` sobe mesmo com a `api-conhecimento` fora do ar.

### Rodar uma API pelo IDE ou terminal, com Swagger UI

Com as variáveis exportadas no shell (as da tabela acima):

```bash
./mvnw -pl api-conhecimento spring-boot:run -Dspring-boot.run.profiles=local
```

No perfil `local`, `/swagger-ui/index.html` e `/v3/api-docs` ficam abertos; nos demais perfis a especificação exige a chave de monitoramento e a Swagger UI fica desligada.

### Conferir rapidamente

```bash
curl -i http://localhost:8081/actuator/health
```

```bash
curl -i http://localhost:8081/api/conhecimentos
```

A segunda chamada deve devolver `401` no formato Problem Details, com o cabeçalho `X-Correlation-Id`.
