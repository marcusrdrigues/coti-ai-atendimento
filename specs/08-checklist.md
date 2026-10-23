# Checklist e registro de implementação

Este arquivo tem duas partes:

1. **Registro de implementação**: o que foi feito em cada fase, com arquivos, testes executados, resultados e pendências. É atualizado ao final de cada fase.
2. **Checklist de produção**: itens bloqueantes antes de publicar em rede (`03-architecture.md` §11.2) e LGPD. Será completado pela tarefa T072.

Convenção: só se registra como "passou" um teste que foi executado, com o comando e o resultado real.

---

## 1. Registro de implementação

### Resumo

| Fase | Status | Tarefas | Concluída em |
|---|---|---|---|
| 1 — Infraestrutura Docker e PostgreSQL/pgvector | **Concluída** | T001–T007 (7/7) | 2026-10-22 |
| 2 — Estrutura Maven e inicialização dos dois microsserviços | Pendente | T008–T018 | — |
| 3 — Persistência e ingestão de conhecimento | Pendente | T019–T027 | — |
| 4 — Chunking e geração de embeddings | Pendente | T028–T036 | — |
| 5 — Busca vetorial e MCP Server | Pendente | T037–T043 | — |
| 6 — MCP Client e API de Atendimentos | Pendente | T044–T053 | — |
| 7 — Integração com OpenAI e contexto RAG | Pendente | T054–T061 | — |
| 8 — Testes, segurança e observabilidade | Pendente | T062–T075 | — |

Tarefas independentes das fases (T066 verificador de segredos e T067 documentos da COTI) continuam abertas.

---

### Fase 1 — Infraestrutura Docker e PostgreSQL/pgvector

**Status:** concluída. **Data:** 2026-10-22. **Tarefas:** T001, T002, T003, T004, T005, T006, T007.

#### O que foi entregue

| Tarefa | Resultado |
|---|---|
| T001 | `.gitignore` com `.env`, `target/`, IDEs e SO; `.env.example` com todos os nomes de `03-architecture.md` §5.1 e nenhum valor; `.gitattributes` forçando LF em `*.sh` e `*.sql` (scripts de init quebram com CRLF no Windows) |
| T002 | Tag `pgvector/pgvector:0.8.7-pg18-trixie` conferida no Docker Hub e com `docker manifest inspect` (publicada em 2026-10-01). A `0.8.2` do plano original existe, mas é a 5ª versão atrás; as specs foram atualizadas para a `0.8.7` |
| T003 | `docker/postgres/init/01-roles.sh`: banco `coti_conhecimento`, papel dono (migrações) e papel da aplicação (só DML), `REVOKE CONNECT` nos bancos padrão, extensões `vector`, `hstore`, `uuid-ossp` criadas como superusuário, privilégios padrão para tabelas futuras do dono |
| T004 | `docker-compose.yml` com o serviço `postgres`: porta `127.0.0.1:5434`, volume `coti-pgdata`, healthcheck, variáveis obrigatórias com mensagem de erro |
| T005 | Validação dos papéis, extensão, DML, DDL e índice HNSW (ver testes abaixo) |
| T006 | `04-plan.md` §3 preenchida com versões e fontes (ver tabela abaixo) |
| T007 | `README.md` com a seção "Ambiente local" |

#### Versões fixadas (T006)

| Item | Valor |
|---|---|
| Spring Boot | 4.1.2 (4.1.1 como alternativa; Spring AI 2.0.1 foi publicado dependendo de `spring-boot-starter` 4.1.1) |
| Spring AI | 2.0.1 |
| springdoc-openapi | 3.1.1 |
| ArchUnit | 1.5.1 |
| WireMock | 3.13.2 |
| Testcontainers | 2.0.5 (gerenciada pelo Boot); `org.testcontainers.postgresql.PostgreSQLContainer` |
| MCP Client | `spring-ai-starter-mcp-client` (Streamable HTTP via cliente HTTP do JDK) |
| PostgreSQL / pgvector | 18.6 / 0.8.7 |

#### Arquivos criados

- `.env.example`
- `.gitattributes`
- `docker-compose.yml`
- `docker/postgres/init/01-roles.sh`
- `.env` (local, **ignorado pelo git**, com senhas geradas; não deve ser commitado)

#### Arquivos modificados

- `.gitignore`: regras de segredos, Maven, IDEs e SO
- `README.md`: seção "Ambiente local"
- `specs/03-architecture.md`: tag pgvector 0.8.7; pendências da seção 15 marcadas como resolvidas
- `specs/04-plan.md`: tabela "Versões fixadas" (§3) preenchida; referências a 0.8.7
- `specs/05-tasks.md`: T001–T007 marcadas `[x]`; T005 com 0.8.7
- `specs/08-checklist.md`: este arquivo
- `AGENTS.md`, `skills/*/SKILL.md`: nenhuma ocorrência de 0.8.2 no momento da troca (a busca por `0.8.2` só encontrou `specs/`)

#### Comandos executados e resultados

| Comando | Resultado |
|---|---|
| `docker version`, `docker compose version`, `java -version` | Docker 29.7.2, Compose v5.5.0, Java 21.0.12 LTS |
| `docker manifest inspect pgvector/pgvector:0.8.2-pg18-trixie` | Manifesto encontrado (a tag existe) |
| `curl` na API de tags do Docker Hub | Tags `0.8.1` a `0.8.7` para pg18; a mais recente é `0.8.7-pg18-trixie` (2026-10-01) |
| `curl` em `maven-metadata.xml` e POMs do Maven Central | Versões da tabela acima |
| `docker compose config --quiet` | Compose válido |
| `docker compose up -d postgres` | Container criado; `healthy` em cerca de 10 s |
| `docker compose down -v` e novo `up -d postgres` | Init reexecutado do zero; `healthy` novamente |
| `docker compose restart postgres` | Volta `healthy`; init não reexecuta |
| `docker compose --env-file .env.example config --quiet` | Falha com "required variable ... is missing a value", como esperado |
| `git check-ignore .env` | `.env` ignorado; `.env.example` não ignorado |
| Varredura dos valores do `.env` nos arquivos não ignorados | Nenhum achado |
| `grep -c $'\r' docker/postgres/init/01-roles.sh` | 0 (fim de linha LF) |

#### Testes de aceite executados (T005)

Clientes `psql` rodaram em um container na rede do compose, para que a **senha seja de fato verificada** (dentro do container do banco, `127.0.0.1` é confiável por padrão na imagem oficial).

| Verificação | Resultado |
|---|---|
| `select version()` | PostgreSQL 18.6 |
| Extensões instaladas | `vector 0.8.7`, `hstore 1.8`, `uuid-ossp 1.1`, `plpgsql 1.0` |
| `pg_available_extensions` para `vector` | `0.8.7` |
| Dono do banco; atributos dos papéis | Dono é o papel de migração; nenhum dos dois é superusuário, nem pode criar banco ou papel |
| Senha errada | `FATAL: password authentication failed` |
| Papel da aplicação em `CREATE TABLE` | `permission denied for schema public` (sem DDL) |
| Papel da aplicação conectando em `postgres` | `permission denied for database "postgres"` (depois do `REVOKE`; antes, conectava) |
| Papel dono: `CREATE TABLE` com `vector(1536)` e índice `HNSW (vector_cosine_ops)` | Sucesso, sem ser superusuário |
| Papel da aplicação: `INSERT` e `SELECT` na tabela criada pelo dono | Sucesso (privilégios padrão funcionam) |
| Papel da aplicação: `ALTER TABLE`, `DROP TABLE` e `CREATE TABLE` | `must be owner of table` / `permission denied for schema public` |
| Porta publicada | `5432/tcp -> 127.0.0.1:5434`; `netstat` mostra `127.0.0.1:5434` em escuta |

Nenhum teste Java foi executado nesta fase: ainda não existe código Java (Fase 2 em diante).

#### Decisões e observações

- **Tag da imagem trocada de 0.8.2 para 0.8.7.** Pedido original: "versão mais atual e estável". É um aumento de patch do pgvector; o índice HNSW com 1536 dimensões foi validado na versão nova.
- **Extensões criadas no init, não pelo Flyway.** `CREATE EXTENSION` exige privilégio que o papel dono não tem. A migração `V1` da Fase 3 pode continuar usando `CREATE EXTENSION IF NOT EXISTS`, que não faz nada se já existir. No Testcontainers, o script de init precisa ser reaproveitado (T020).
- **Volume em `/var/lib/postgresql`**, não em `/var/lib/postgresql/data`: exigência da imagem PostgreSQL 18.
- **Hardening além do pedido:** `REVOKE CONNECT` nos bancos `postgres` e `template1`, e recusa de papéis iguais para migração e aplicação.
- **Autenticação `trust` dentro do container** para `127.0.0.1` e socket é o padrão da imagem oficial. Quem tem acesso a `docker exec` já é administrador; conexões de fora usam senha.

#### Pendências e riscos

- `.env` local foi criado com senhas geradas para os testes. Os valores não foram exibidos. Cada desenvolvedor gera o seu a partir do `.env.example`.
- O Spring AI 2.0.1 foi publicado contra o Spring Boot 4.1.1; o plano usa 4.1.2. A compatibilidade será provada em T009 (`./mvnw verify`); se falhar, voltar para 4.1.1.
- Já existe Spring AI 2.0.2. Não foi adotada porque a versão 2.0.1 é requisito do projeto. Vale decidir se aceita a 2.0.2 (correções).
- WireMock 3.13.2 usa Jackson 2; o Boot 4 usa Jackson 3. Conferir ao introduzir o WireMock (T050/T056).
- T066 (verificador de segredos) e T067 (documentos reais da COTI) seguem abertas e independentes das fases; T067 bloqueia o eval.
- Itens "(conferir)" de `03-architecture.md` continuam abertos para os spikes T031, T037, T044 e T061.

#### Próxima fase

**Fase 2 — Estrutura Maven e inicialização dos dois microsserviços** (T008–T018). A primeira tarefa é a **T008** (`pom.xml` agregador e Maven Wrapper); depois T009 e T010 em paralelo.

---

## 2. Checklist de produção (a completar na T072)

Itens que `03-architecture.md` §11.2 torna bloqueantes antes de publicar fora da máquina do desenvolvedor. Nenhum está concluído.

- [ ] TLS em toda conexão externa
- [ ] `/mcp` inacessível de fora
- [ ] Autenticação administrativa forte (OAuth2/OIDC) e trilha de auditoria
- [ ] Segredos em cofre, com rotação
- [ ] Limite de requisições distribuído e teto diário de custo na OpenAI
- [ ] Swagger UI desligada e `/v3/api-docs` autenticado
- [ ] Actuator em rede de gestão
- [ ] CORS restrito, se houver front-end
- [ ] Banco sem porta publicada, backups cifrados, `sslmode=require`
- [ ] Varredura de dependências e imagens no CI; imagens com digest fixo
- [ ] LGPD: contrato com a OpenAI como operadora (sem treino, retenção definida, transferência internacional, art. 33); prazos de retenção de log confirmados
- [ ] Revisão OWASP Top 10 para LLM com evidências (T073)
- [ ] Eval na nota mínima e caminho testado de volta ao prompt/modelo anterior (T069, T070)
