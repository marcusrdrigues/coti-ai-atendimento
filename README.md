# COTI AI Atendimento

Plataforma de atendimento inteligente para alunos da COTI Informática, formada por dois microsserviços Spring Boot independentes:

- **api-conhecimento** (porta 8081): base de conhecimento, chunking, embeddings e busca vetorial no PostgreSQL/pgvector; expõe a ferramenta MCP `buscar_conhecimento`.
- **api-atendimentos** (porta 8082): recebe perguntas por REST, consulta a base **somente por MCP** e gera a resposta com Spring AI e OpenAI.

A especificação completa está em [`specs/`](specs/) (constituição, requisitos, arquitetura, plano, tarefas e contratos). Agentes de codificação seguem o [`AGENTS.md`](AGENTS.md).

> Estado atual: Fases 1 a 7 concluídas. A `api-atendimentos` recebe a pergunta, mascara dados pessoais, busca os trechos pela ferramenta MCP, monta o contexto (limiar 0,70, no máximo 5 trechos), gera a resposta com o modelo de chat (sem ferramentas, saída estruturada), valida a saída em código (referência inventada, preço, data, horário ou vagas sem evidência são reprovados) e devolve a resposta com as fontes copiadas do MCP. Sem evidência, ou com saída reprovada três vezes, responde a mensagem configurada; se uma dependência falha, devolve `503` com `Retry-After`. Ela sobe mesmo com a `api-conhecimento` fora do ar (saúde `DEGRADADO`). Todos os testes usam OpenAI simulada; a requisição ao `gpt-6-luna` real foi aceita numa chamada de fumaça (2026-10-27), mas **a qualidade das respostas ainda não foi medida** (depende dos documentos reais e do eval da Fase 8). Acompanhe em [`specs/05-tasks.md`](specs/05-tasks.md).

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

## Remover um conhecimento da base

A remoção por API está fora do MVP. Para apagar um documento e todos os seus trechos (e rodar o `VACUUM` que o índice vetorial precisa depois de remoções), use o script, que lê as credenciais do dono do esquema no `.env` e pede confirmação:

```bash
scripts/remover-conhecimento.sh <id-do-documento>
```

## Limites de requisição

`POST /api/atendimentos/perguntar`: 10 por minuto por IP. `POST /api/conhecimentos`: 30 por minuto por credencial. `POST /api/conhecimentos/diagnostico/busca`: 10 por minuto por credencial. Acima disso a resposta é `429` com `Retry-After`. Os limites são em memória e por instância.

## Verificação de segredos

O [gitleaks](https://github.com/gitleaks/gitleaks) (licença MIT) procura chaves, senhas e tokens no código e no histórico. A configuração do repositório é o `.gitleaks.toml` (regras padrão, sem exceções). O `.env` nunca vai para o git: está no `.gitignore`, e só o `.env.example` (sem valores) é versionado.

Instalação no Windows: `winget install Gitleaks.Gitleaks` (abra um terminal novo depois). Varredura do histórico inteiro, com o valor mascarado:

```bash
gitleaks git --config .gitleaks.toml --redact --no-banner
```

Para bloquear o commit que contém segredo, use um hook de `pre-commit` que rode `gitleaks git --pre-commit --staged --redact --no-banner`. O repositório não versiona o hook (ele fica no `core.hooksPath` de cada pessoa); quem clonar precisa configurar o seu. Um falso positivo se resolve com o comentário `gitleaks:allow` na linha. Não use `--no-verify` para pular o hook. A etapa no CI ainda não existe, porque o repositório ainda não tem CI.
