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
| 2 — Estrutura Maven e inicialização dos dois microsserviços | **Concluída** | T008–T018 (11/11) | 2026-10-22 |
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

### Fase 2 — Estrutura Maven e inicialização dos dois microsserviços

**Status:** concluída. **Data:** 2026-10-22. **Tarefas:** T008 a T018 (11 de 11).

#### O que foi entregue

| Tarefa | Resultado |
|---|---|
| T008 | `pom.xml` agregador (só `<modules>`, não é parent) e Maven Wrapper 3.9.16 (`only-script`; o JAR do wrapper fica fora do git) |
| T009 | `api-conhecimento`: parent `spring-boot-starter-parent` 4.1.2, BOM do Spring AI 2.0.1, porta 8081, virtual threads, logs ECS, `spring.ai.model.chat: none` |
| T010 | `api-atendimentos`: mesma base, porta 8082, `spring.ai.model.embedding: none`, sem JDBC, `max-tokens` 600 e temperatura 0,0 |
| T011 | `PropriedadesConhecimento` / `PropriedadesAtendimentos` (`record` + `@Validated`); a subida falha citando o nome da variável |
| T012 | `CorrelacaoFilter`: `X-Correlation-Id` em toda resposta (derivado do `traceparent` válido, senão UUID novo) e MDC |
| T013 | `CodigoErro`, `ProblemaFactory`, `EscritorProblema`, `ManipuladorErrosRest`, `ErroContainerController`, `LimiteTamanhoCorpoFilter` (256 KB) |
| T014 | `SecurityConfig` com cadeias de monitoramento/docs, API e padrão; `ApiKeyAuthFilter`, `ComparadorChave`, `RespostaSegurancaProblema` |
| T015 | Actuator expondo `health`, `info` e `metrics`; métricas e detalhes só com `MONITORAMENTO_API_KEY` |
| T016 | springdoc 3.1.1; `OpenApiConfig`; Swagger UI só no perfil `local` |
| T017 | `ArquiteturaTest` (ArchUnit) nos dois serviços e `IsolamentoDeDependenciasTest` na `api-atendimentos` |
| T018 | `Dockerfile` multi-stage (usuário `10001`), `.dockerignore`, `docker-compose.yml` com as duas APIs |

#### Arquivos criados

- Raiz: `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
- Em cada serviço: `pom.xml`, `Dockerfile`, `.dockerignore`, `src/main/resources/application.yml` e `application-local.yml`
- `api-conhecimento/src/main/java/br/com/coti/conhecimento/`: `ApiConhecimentoApplication`; `api/erro/` (`CodigoErro`, `CorrelacaoHttp`, `CorpoGrandeDemaisException`, `ErroCampo`, `ErroContainerController`, `EscritorProblema`, `ManipuladorErrosRest`, `ProblemaFactory`); `infraestrutura/config/` (`PropriedadesConhecimento`, `OpenApiConfig`); `infraestrutura/observabilidade/CorrelacaoFilter`; `infraestrutura/seguranca/` (`ApiKeyAuthFilter`, `ComparadorChave`, `LimiteTamanhoCorpoFilter`, `RespostaSegurancaProblema`, `SecurityConfig`)
- `api-atendimentos/src/main/java/br/com/coti/atendimentos/`: as mesmas classes, com `PropriedadesAtendimentos`, o catálogo de erros da API (inclui `TEMPO_ESGOTADO`) e um `SecurityConfig` com a rota pública de perguntas
- Testes de cada serviço: `suporte/TesteWeb`, `suporte/ChavesTeste`, `api/erro/ManipuladorErrosRestTest`, `arquitetura/ArquiteturaTest`, `infraestrutura/config/{ConfiguracaoSpringAiTest, InicializacaoSemVariavelTest, OpenApiTest}`, `infraestrutura/observabilidade/CorrelacaoFilterTest`, `infraestrutura/seguranca/{ActuatorAcessoTest, ComparadorChaveTest, LimiteTamanhoCorpoFilterTest, SegurancaTest}`; na `api-atendimentos`, também `arquitetura/IsolamentoDeDependenciasTest`

#### Arquivos modificados

- `README.md`: variáveis, build, testes, compose e perfil `local`
- `docker-compose.yml`: serviços `api-conhecimento` e `api-atendimentos`
- `specs/03-architecture.md`: nota sobre `api.erro` em §3.1
- `specs/05-tasks.md`: T008–T018 marcadas `[x]`; T019 passa a incluir JDBC e Flyway (adiados da T009)
- `specs/08-checklist.md`: este registro

#### Comandos executados e resultados

| Comando | Resultado |
|---|---|
| `./mvnw -v` | Maven 3.9.16, JDK 21.0.12 |
| `./mvnw verify` (raiz) | **BUILD SUCCESS**: `api-conhecimento` 60 testes, 0 falhas, 1 ignorado; `api-atendimentos` 61 testes, 0 falhas, 1 ignorado |
| `./mvnw -pl api-conhecimento test -Dtest=ArquiteturaTest` com uma classe temporária em `dominio` que importava `@Component` | **Falhou**, como esperado (regra do domínio violada); classe removida; o teste voltou a passar (3 de 3) |
| `env -u OPENAI_API_KEY docker compose up -d --build` | Duas imagens construídas; os 3 contêineres ficaram `healthy` |
| `curl` em `:8081` e `:8082`, `/actuator/health` | 200 `{"groups":["liveness","readiness"],"status":"UP"}`, com `X-Correlation-Id` |
| `curl http://localhost:8081/api/conhecimentos` | 401 `application/problem+json` com `codigo`, `correlacaoId` e `timestamp` |
| `curl -X POST http://localhost:8082/api/atendimentos/perguntar` | 404 (a rota é pública; ainda não há controlador) |
| `curl http://localhost:8081/actuator/env` sem chave | 401 |
| `docker exec ... id` nos dois contêineres | `uid=10001(coti)` |
| Parar `coti-api-conhecimento` e consultar a saúde da `api-atendimentos` | 200 `UP` (serviço independente) |

#### Testes automatizados (resumo)

| Área | Cobertura | Serviços |
|---|---|---|
| Erros | 400 (validação, campo desconhecido, JSON malformado, tipo de parâmetro, parâmetro obrigatório, violação de restrição), 404, 405, 415, 413, 500 genérico sem detalhe interno, `/error` do contêiner e ausência do `BasicErrorController`; o valor recebido nunca aparece na mensagem | os dois |
| Segurança | 401 sem chave, chave errada, chaves trocadas, negar por padrão (`/mcp` e rotas desconhecidas em 401), sem cookie, sem CSRF, cabeçalhos padrão | os dois |
| Actuator | health público só com `status` e `groups`; componentes com chave; liveness e readiness; métricas só com chave; `env`, `configprops`, `beans`, `heapdump`, `threaddump`, `mappings`, `loggers` e `shutdown` não expostos | os dois |
| Correlação | `traceparent` válido, ausente, inválido e zerado; id do cliente ignorado; MDC limpo (inclusive com falha na cadeia) | os dois |
| Limite de corpo | por `Content-Length`, no limite, e fluxo sem `Content-Length` (chunked) | os dois |
| Inicialização | variável ausente e em branco, para cada obrigatória, citando o nome e nunca o valor | os dois |
| Spring AI | `EmbeddingModel` só na `api-conhecimento`; `ChatModel` só na `api-atendimentos`; `max-retries`, modelo, dimensão, temperatura | os dois |
| OpenAPI | perfil padrão (especificação com chave, sem Swagger UI) e perfil `local` | os dois |
| Arquitetura | domínio puro, aplicação sem web/IA/banco, camadas; `api-atendimentos` sem banco, vetores nem embeddings; classes de JDBC, PostgreSQL, pgvector, JPA e Flyway ausentes do classpath | os dois |

**Teste ignorado (1 por serviço):** a variante "variável ausente" de `OPENAI_API_KEY` não roda quando a variável existe no ambiente do SO (a suposição `assumeThat` a pula), e neste computador ela está definida. A variante "em branco" da mesma variável roda e passa, e o mecanismo de "ausente" é o mesmo das outras duas variáveis, que rodam. Para executá-la, rode o build em um shell sem essa variável.

#### Decisões e desvios

- **JDBC, Flyway e driver PostgreSQL adiados para a T019.** A T009 listava `spring-boot-starter-jdbc`, mas sem banco e sem `DataSource` o contexto não sobe, e os testes da Fase 2 não têm Testcontainers (T020). A T019 foi ajustada.
- **Starter da OpenAI já na Fase 2** (previsto no risco da fase), para provar `spring.ai.model.chat: none`, `spring.ai.model.embedding: none` e o formato das propriedades da 2.0.
- **Placeholder não resolvido era aceito como chave** (achado pelo `InicializacaoSemVariavelTest`): quando a variável não existe, o Spring mantém o texto literal `${NOME}` ao vincular `@ConfigurationProperties`. Sem a correção, a chave administrativa seria o próprio nome da variável. As propriedades agora recusam valor em branco **e** valor que começa com `${`.
- **Correlação sem biblioteca de tracing.** O `CorrelacaoFilter` aceita um `traceparent` W3C e gera o UUID; o tracer do Micrometer (e a propagação para o MCP) fica para a T065, porque é dependência nova.
- **`api.erro` acessível pela infraestrutura.** As camadas do `03-architecture.md` proibiam `infraestrutura → api`, mas segurança e filtros precisam escrever 401, 403 e 413 no formato do contrato. `api.erro` virou a "linguagem de erro HTTP comum" (nota em §3.1 e regra ArchUnit).
- **Health público devolve `groups`** (`liveness`, `readiness`) além de `status`, sem componentes nem detalhes. O contrato mostra só `status`; a diferença é inofensiva.
- **Rota pública da `api-atendimentos` aceita qualquer método**, para um `GET` receber o 405 do contrato, e não 401.
- **Prometheus fora do exposure.** O endpoint exige o registro `micrometer-registry-prometheus`, dependência nova (T063).
- **Testes de erro usam um controlador de teste** em cada serviço, importado só pela classe de teste (`/api/teste/**` na `api-conhecimento`; a rota de perguntas na `api-atendimentos`). Quando os controladores reais existirem (Fases 3 e 6), esses testes devem passar a usar rotas reais.
- **Pacotes do Spring Boot 4 confirmados na prática:** `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, `org.springframework.boot.webmvc.error.ErrorController`, `org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController` e Jackson 3 (`tools.jackson...`). Classes internas de teste não entram na varredura de componentes.
- **Boot 4.1.2 com Spring AI 2.0.1 compilou e executou sem ajuste**; o alerta da Fase 1 sobre o Boot 4.1.1 não se confirmou.

#### Pendências e riscos

- `OPENAI_API_KEY` real existe no ambiente do SO deste computador, e o Compose a usaria no lugar da chave do `.env`. Subi o compose com `env -u OPENAI_API_KEY` para os contêineres receberem a chave falsa. Nenhuma chamada à OpenAI é feita nesta fase.
- O `.env` local tem valores gerados (chaves de API de teste e senhas) e valores provisórios (`OPENAI_API_KEY` falsa e `OPENAI_CHAT_MODEL=modelo-provisorio-ate-a-T070`); está ignorado pelo git e não foi exibido.
- Perfil `local`: coberto pelos testes de OpenAPI; não rodei uma instância real com `-Dspring-boot.run.profiles=local` (os contêineres usam o perfil padrão).
- `/v3/api-docs`: no perfil padrão exige a chave de monitoramento; no `local` é pública (alinhado ao item 6 da seção 11.2 da arquitetura).
- `Retry-After` em 429 e 503 ainda não existe: entra junto com os fluxos que geram esses códigos (Fases 6 e 8).
- A `ManipuladorErrosRest` e os filtros estão duplicados nos dois serviços, por desenho (os serviços não compartilham código).
- O commit `31cff7c` com os arquivos das Fases 1 e 2 foi feito durante a sessão, não por mim.

#### Próxima fase

**Fase 3 — Persistência e ingestão de conhecimento** (T019–T027). A primeira tarefa é a **T019**: JDBC, Flyway e PostgreSQL com as migrações V1 a V3.

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
