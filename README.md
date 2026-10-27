# COTI AI Atendimento

Plataforma de atendimento inteligente para alunos da COTI Informática, formada por dois microsserviços Spring Boot independentes:

- **api-conhecimento** (porta 8081): base de conhecimento, chunking, embeddings e busca vetorial no PostgreSQL/pgvector; expõe a ferramenta MCP `buscar_conhecimento`.
- **api-atendimentos** (porta 8082): recebe perguntas por REST, consulta a base **somente por MCP** e gera a resposta com Spring AI e OpenAI.

A especificação completa está em [`specs/`](specs/) (constituição, requisitos, arquitetura, plano, tarefas e contratos). Agentes de codificação seguem o [`AGENTS.md`](AGENTS.md).

> Estado atual: Fases 1 a 6 concluídas. A `api-conhecimento` cadastra, divide e indexa documentos, busca por similaridade e expõe a ferramenta MCP `buscar_conhecimento`. A `api-atendimentos` recebe a pergunta, mascara dados pessoais, chama a ferramenta MCP, responde "sem evidência" quando a base não tem o assunto e devolve `503` com `Retry-After` quando uma dependência falha; ela sobe mesmo com a `api-conhecimento` fora do ar (saúde `DEGRADADO`). Uma pergunta que encontra trechos ainda termina em `503 MODELO_INDISPONIVEL`, porque a geração da resposta com o modelo de IA chega na Fase 7. Cadastro e busca chamam a OpenAI de verdade (`text-embedding-3-small`); acompanhe em [`specs/05-tasks.md`](specs/05-tasks.md).

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
| `OPENAI_API_KEY` | Chave da OpenAI (a `api-conhecimento` a usa ao cadastrar, desde a Fase 4, e a `api-atendimentos` ao responder, na Fase 7; a subida exige a variável) |
| `OPENAI_CHAT_MODEL` | Identificador do modelo de chat, com versão (`api-atendimentos`); será definido pelo eval (T070) |
| `CONHECIMENTO_ADMIN_API_KEY` | Chave administrativa (`X-Api-Key`) da `api-conhecimento` |
| `MONITORAMENTO_API_KEY` | Chave (`X-Api-Key`) para métricas e detalhes do health |
| `CONHECIMENTO_MCP_API_KEY` | Chave de serviço (`Authorization: Bearer`) com que a `api-atendimentos` chama a ferramenta MCP da `api-conhecimento`; a chave administrativa não vale em `/mcp` |
| `ATENDIMENTO_MENSAGEM_SEM_EVIDENCIA` | Texto devolvido ao aluno quando não há evidência na base (`api-atendimentos`), por exemplo `Não encontrei essa informação nos materiais oficiais da COTI.` |
| `ATENDIMENTO_CANAL_HUMANO` | Canal de atendimento humano, acrescentado à mensagem acima (`api-atendimentos`), por exemplo `Fale com a nossa equipe: https://www.coti.com.br/contato` |

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

### Compilar e testar (exige o Docker Desktop ativo)

Os testes da `api-conhecimento` sobem o PostgreSQL 18 com pgvector em Testcontainers (a imagem é baixada na primeira vez). Nenhum teste chama a OpenAI.

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
| `api-conhecimento` | `http://localhost:8081` | `GET /actuator/health` público; `/api/conhecimentos` exige `X-Api-Key`; aplica as migrações do banco ao subir |
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
