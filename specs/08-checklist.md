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
| 3 — Persistência e ingestão de conhecimento | **Concluída** | T019–T027 (9/9) | 2026-10-23 |
| 4 — Chunking e geração de embeddings | **Concluída** | T028–T036 (9/9) | 2026-10-26 |
| 5 — Busca vetorial e MCP Server | **Concluída** | T037–T043 (7/7) | 2026-10-27 |
| 6 — MCP Client e API de Atendimentos | **Concluída** | T044–T053 (10/10) | 2026-10-27 |
| 7 — Integração com OpenAI e contexto RAG | Concluída | T054–T061 | 2026-10-27 |
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

---

### Fase 3 — Persistência e ingestão de conhecimento

**Status:** concluída. **Data:** 2026-10-23. **Tarefas:** T019 a T027 (9 de 9).

#### O que foi entregue

| Tarefa | Resultado |
|---|---|
| T019 | `spring-boot-starter-jdbc`, `spring-boot-starter-flyway`, `flyway-database-postgresql` e driver PostgreSQL; migrações `V1__extensoes`, `V2__documento` (CHECKs de status, categoria e trechos; hash único; 3 índices), `V3__vector_store` (`vector(1536)`, HNSW cosseno, índice por `documentoId`); Flyway com o papel dono e a aplicação com o papel de DML |
| T020 | `PostgresContainerConfig` (Testcontainers com a imagem pgvector 0.8.7-pg18, papéis criados pelo mesmo `01-roles.sh` do Compose, senhas aleatórias por execução) e `MigracoesIT` |
| T021 | Domínio: `Documento` (invariantes e transições), `StatusDocumento`, `Categoria`, `Pendencia`, `DocumentoResumo`, `FiltroDocumentos`, `Pagina`, `ResultadoAnalise`, `ModeloEmbedding` e exceções |
| T022 | `NormalizadorTexto` (NFC, LF, sem controles nem caracteres invisíveis, espaços e linhas em branco) e `HashConteudo` (SHA-256) |
| T023 | `AnalisadorConteudoPort` e `AnalisadorConteudoRegex`: CPF com dígito verificador, e-mail e telefone com listas de permissão, 14 padrões de injeção configuráveis no `application.yml` |
| T024 | `RepositorioDocumentoPort` e `DocumentoJdbcRepositorio` (`JdbcClient`, parâmetros vinculados) |
| T025 | `CadastrarConhecimentoService`: normaliza, confere duplicidade, analisa, retém em revisão ou grava `PROCESSANDO`, indexa e publica |
| T026 | `ConhecimentoController` `POST /api/conhecimentos`, DTOs `record` com Bean Validation, `ManipuladorExcecoesConhecimento` (409, 422, 404, 400, 503) |
| T027 | `ConsultarConhecimentosService`, `GET /api/conhecimentos` (filtros e paginação) e `GET /api/conhecimentos/{id}` |

#### Arquivos criados

- `api-conhecimento/src/main/resources/db/migration/V1__extensoes.sql`, `V2__documento.sql`, `V3__vector_store.sql`
- Domínio (`dominio/`): `Categoria`, `Pendencia`, `StatusDocumento`, `Documento`, `DocumentoResumo`, `FiltroDocumentos`, `Pagina`, `ResultadoAnalise`, `ModeloEmbedding`, `NormalizadorTexto`, `HashConteudo`; `dominio/excecao/`: `ConhecimentoDuplicadoException`, `DadoPessoalDetectadoException`, `ConhecimentoNaoEncontradoException`, `IndexacaoFalhouException`, `DadosInvalidosException`
- Aplicação (`aplicacao/`): `CadastrarConhecimentoService`, `CadastrarConhecimentoComando`, `ConsultarConhecimentosService`; portas `RepositorioDocumentoPort`, `AnalisadorConteudoPort`, `IndexacaoDocumentoPort`
- Infraestrutura: `persistencia/DocumentoJdbcRepositorio`, `analise/AnalisadorConteudoRegex`, `indexacao/IndexadorNaoImplementado` (provisório), `config/ConhecimentoConfig`
- API: `api/rest/ConhecimentoController`, `api/rest/ManipuladorExcecoesConhecimento`, DTOs `CadastrarConhecimentoRequest`, `ConhecimentoCadastradoResponse`, `ConhecimentoResumo`, `ConhecimentoDetalheResponse`, `PaginaConhecimentosResponse`
- Testes: `suporte/PostgresContainerConfig`; `dominio/{DocumentoTest, NormalizadorTextoTest}`; `aplicacao/{RepositorioDocumentoEmMemoria, CadastrarConhecimentoServiceTest, ConsultarConhecimentosServiceTest}`; `infraestrutura/analise/AnalisadorConteudoRegexTest`; `infraestrutura/persistencia/{MigracoesIT, DocumentoJdbcRepositorioIT}`; `api/rest/{ConhecimentoControllerTest, CadastroSemIndexadorIT, ConsultarConhecimentosIT}`

#### Arquivos modificados

- `api-conhecimento/pom.xml`: JDBC, Flyway, PostgreSQL, Testcontainers; Surefire passa a executar também `*IT`; versões do `swagger-annotations` alinhadas (ver decisões)
- `api-atendimentos/pom.xml`: só o alinhamento do `swagger-annotations`
- `api-conhecimento/src/main/resources/application.yml`: datasource, Flyway, Hikari, `default-property-inclusion: non_null`, modelo de embedding e padrões de análise
- `infraestrutura/config/PropriedadesConhecimento`: `Embedding` e `Analise`
- `suporte/TesteWeb` (agora com o PostgreSQL em Testcontainers), `InicializacaoSemVariavelTest`, `LimiteTamanhoCorpoFilterTest` e `SegurancaTest` (a rota `/api/conhecimentos` agora existe)
- `docker-compose.yml`: banco e `depends_on` na `api-conhecimento`
- `README.md`, `specs/03-architecture.md` (nota sobre `PROCESSANDO`), `skills/testing/SKILL.md` (os `*IT` rodam no Surefire), `specs/05-tasks.md` e este arquivo

#### Comandos executados e resultados

| Comando | Resultado |
|---|---|
| `./mvnw -B verify` (raiz) | **BUILD SUCCESS**: `api-conhecimento` **237 testes, 0 falhas, 1 ignorado**; `api-atendimentos` 61 testes, 0 falhas, 1 ignorado |
| `docker compose up -d --build api-conhecimento` (com `env -u OPENAI_API_KEY`) | Contêiner `healthy`; log: "Successfully validated 3 migrations" |
| Consulta como papel da aplicação no banco do Compose | `flyway_schema_history`: V1, V2, V3 com sucesso; tabelas `documento`, `flyway_schema_history` e `vector_store`, todas do papel dono |
| `GET /actuator/health` com a chave de monitoramento | componente `db` em `UP` |
| `POST /api/conhecimentos` com texto de injeção (arquivo UTF-8) | `202` `EM_REVISAO`, `pendencias=["POSSIVEL_PROMPT_INJECTION"]` |
| mesmo arquivo de novo | `409` `CONHECIMENTO_DUPLICADO` |
| texto com CPF válido | `422` `DADO_PESSOAL_DETECTADO` (CPF não aparece na resposta nem no log) |
| cadastro sem pendências | `503` `MODELO_INDISPONIVEL` com `conhecimentoId`; documento fica `ERRO_PROCESSAMENTO` (esperado: indexação só na Fase 4) |
| `GET /api/conhecimentos?tamanho=101`, id inválido, id inexistente, sem chave | `400`, `400`, `404`, `401` |
| `grep` nos logs do contêiner por trechos dos documentos e pelo CPF | 0 ocorrências; só metadados (`conhecimentoId`, pendências) |
| `DELETE FROM documento` pelo papel da aplicação | removeu as 3 linhas de teste do banco local |

#### Testes automatizados (resumo)

| Área | Cobertura |
|---|---|
| Migrações (banco real) | histórico Flyway, extensões, `vector(1536)`, HNSW cosseno, colunas e índices; aplicação sem DDL (`42501` em CREATE, ALTER e DROP); DML permitido nas tabelas do dono; dono não é superusuário; aplicação sem acesso ao banco `postgres`; CHECKs de status, categoria e trechos; hash único |
| Domínio | invariantes do `Documento`, transições de estado, imutabilidade, resumo sem conteúdo, paginação |
| Normalizador e hash | CRLF, controles, caracteres invisíveis, NFC, espaços, linhas em branco, equivalência de hash, vetor SHA-256 conhecido |
| Analisador | CPF válido e inválido, e-mail, telefone (com e sem separadores, DDD inválido, números corridos), listas de permissão, 18 textos de injeção e 8 textos comuns que não podem disparar |
| Casos de uso | cadastro feliz, ordem das chamadas, texto normalizado, conteúdo curto ou grande, duplicidade, dado pessoal antes da injeção, retenção em revisão, falhas de indexação e de marcação de erro; listagem (ordem, filtros, paginação, limites, `PROCESSANDO` oculto) e detalhe |
| Repositório (banco real) | ida e volta de todos os campos, acentos, pendências, vigência, hash duplicado, atualização de estado, listagem, filtros, paginação, ordem estável, SQL injection nos parâmetros |
| `POST /api/conhecimentos` | 201 com `Location`, 202, 400 por regra e por campo desconhecido sem eco do valor, 401 (sem chave, errada, de monitoramento), 409, 413, 415, 422 (CPF, e-mail, telefone), 503 com `conhecimentoId` e `Retry-After` |
| `GET` | filtros e combinações, paginação, `tamanho` 0, -1, 100 e 101, parâmetros malformados, listagem sem `conteudo`, detalhe com e sem pendências, 404, 400 e 401 |

**Teste ignorado (1 por serviço):** a variante "ausente" de `OPENAI_API_KEY` no `InicializacaoSemVariavelTest`, porque a variável existe no ambiente deste computador (igual à Fase 2).

#### Decisões e desvios

- **Indexação provisória.** A porta `IndexacaoDocumentoPort` tem, nesta fase, o adaptador `IndexadorNaoImplementado`, que sempre falha. Um cadastro sem pendências termina em `ERRO_PROCESSAMENTO` com `503`, em vez de parecer publicado sem trechos. É removido na T035. O `CadastroSemIndexadorIT` documenta esse comportamento e deve mudar na Fase 4.
- **`PROCESSANDO` é invisível na API.** A listagem o omite, o detalhe responde `404` e filtrar por ele devolve `400`. O contrato só tem 3 estados públicos; documentos interrompidos nesse estado serão recolhidos pela T036.
- **Número corrido não é telefone.** Dez dígitos sem parênteses, `+` nem separadores (como um protocolo) não disparam o detector; o teste manual com um carimbo de data de 10 dígitos mostrou o falso positivo. Celular de 11 dígitos começando por 9 continua sendo detectado.
- **Padrões de injeção estreitados.** "Você agora é aluno" e "a partir de agora, você pode se matricular" são texto comum de curso e não disparam mais; só "você agora é um assistente/bot/..." e "a partir de agora você deve/vai/precisa...".
- **Teste com banco real em toda a suíte web.** O `TesteWeb` passou a subir o PostgreSQL em Testcontainers (um contêiner por JVM). Por isso `./mvnw test` da `api-conhecimento` exige o Docker ativo. Usa propriedades dinâmicas e não `@ServiceConnection`, porque a aplicação precisa de dois papéis (Flyway e aplicação).
- **Surefire executa os `*IT`.** O Failsafe não está configurado, então os `*IT` seriam ignorados em silêncio. O Surefire agora inclui `**/*Test.java` e `**/*IT.java`. A skill `testing` foi corrigida.
- **Conflito de versões do `swagger-annotations`** (achado pelo teste `OpenApiTest` ao existir o primeiro controller): o Spring AI traz `swagger-annotations` 2.2.31 e `-jakarta` 2.2.38, e o springdoc 3.1.1 usa `swagger-core` 2.2.55. Com isso, `/v3/api-docs` falhava com `NoSuchMethodError: Schema.$dynamicRef()`. As duas peças foram fixadas em 2.2.55 nos dois serviços (alinhamento de versão, não dependência nova).
- **Hikari em milissegundos.** `connection-timeout: 3s` não é aceito (o valor é `long`); ficou `3000`.
- **Transação de leitura.** `ConsultarConhecimentosService` usa `@Transactional(readOnly = true)` e por isso não é `final` (o proxy de classe exige). Os demais serviços e adaptadores são `final`; o repositório usa `@Component` (e não `@Repository`) para não ser proxiado.
- **Pacote `org.springframework.dao` na camada `api`.** O `ManipuladorExcecoesConhecimento` traduz falha de acesso ao banco em `503 BASE_CONHECIMENTO_INDISPONIVEL` com `Retry-After`; a regra do ArchUnit continua passando.

#### Pendências e riscos

- **Detecção de dado pessoal pode bloquear contatos institucionais.** E-mail e telefone da própria COTI num documento institucional dão `422`. As listas `dominios-email-permitidos` e `telefones-permitidos` estão vazias no `application.yml`: falta a COTI informar os canais oficiais (domínio e telefones).
- **Chave de serviço MCP ainda não existe.** O teste "chave MCP recusada" usa a chave de monitoramento; a cadeia `/mcp` e a variável `CONHECIMENTO_MCP_API_KEY` entram na Fase 5.
- **Sem `Retry-After` em 429** (limite de requisições é da Fase 8). O 503 de indexação e de banco já devolve `Retry-After: 30`.
- **Reexecução na Fase 4:** os testes `CadastroSemIndexadorIT` e o `@MockitoBean` de `IndexacaoDocumentoPort` nos testes do controller devem ser revistos quando a indexação real existir.
- **Dependência de Docker nos testes.** Quem rodar `./mvnw test` sem o Docker ativo vê falha de contexto em toda a suíte web da `api-conhecimento`.
- **Data/hora da resposta do `POST`** sai com precisão de nanossegundos (relógio do contêiner), enquanto o banco guarda microssegundos; o `GET` devolve microssegundos. Inofensivo.
- O `.env` local ganhou chaves geradas e a `OPENAI_API_KEY` provisória; continua ignorado pelo git.

#### Próxima fase

**Fase 4 — Chunking e geração de embeddings** (T028–T036). A primeira tarefa é a **T028** (`DivisorTexto` com sobreposição); a **T031** (spike do Spring AI 2.0.1 sobre `metadata`, `delete(Filter.Expression)` e `add()` com id repetido) pode ser feita em seguida, pois precisa do banco já migrado.

---

### Fase 4 — Chunking e geração de embeddings

**Status:** concluída. **Data:** 2026-10-26. **Tarefas:** T028 a T036 (9 de 9).

#### O que foi entregue

| Tarefa | Resultado |
|---|---|
| T028 | `DivisorTexto` e `ConfiguracaoDivisao` (1.500 / 200 / 50 / 500 em `application.yml`): corte por parágrafo, fim de frase, quebra de linha e espaço, sobreposição iniciando em começo de palavra, recusa com `DocumentoGrandeDemaisException` |
| T029 | `IdTrecho` (UUID v5 de `documentoId:ordem`) e `VigenciaMetadado` (`AAAAMMDD`, `99991231` sem vigência) |
| T030 | `EmbeddingModelFalso` determinístico (1536 dimensões) com modos lento, falha nas próximas N chamadas, falha sempre e falha só na chamada N; exceções reais do SDK da OpenAI (429, 5xx, 401, tempo esgotado) |
| T031 | Spike do Spring AI 2.0.1 versionado em `PgVectorStoreComportamentoIT`; resultados em `04-plan.md` §3 |
| T032 | `spring-ai-starter-vector-store-pgvector` (dependência já aprovada na §13 da arquitetura), `PgVectorStore` com `initialize-schema=false`, `schema-validation=true`, HNSW, cosseno; contexto de teste sobe com o dublê |
| T033 | `RepositorioVetorialPort` e `PgVectorRepositorioAdapter`: gravação em lote com metadados, `promoverTrechos`, `removerPorDocumento`; `TradutorFalhasEmbedding` converte falhas do SDK em exceções de domínio (transitória ou definitiva) |
| T034 | Publicação atômica por `PublicarDocumentoService` (ver desvios) |
| T035 | `PoliticaNovasTentativas`, `IndexarDocumentoService` (lotes de 100) e `CadastrarConhecimentoService` completo: divisão → inserção `PROCESSANDO` → embeddings → publicação → 201; compensação → `ERRO_PROCESSAMENTO` e 503 `MODELO_INDISPONIVEL` (com `conhecimentoId` e `Retry-After`); 413 acima de 500 trechos |
| T036 | `LimpezaDocumentosInterrompidos` (`ApplicationRunner`) sobre `RecolherDocumentosInterrompidosService`: `PROCESSANDO` há mais de 10 minutos vira `ERRO_PROCESSAMENTO` e perde os trechos |

#### Arquivos criados (em `api-conhecimento/src`)

- `main`: `dominio/{ConfiguracaoDivisao, DivisorTexto, IdTrecho, Trecho, VigenciaMetadado}`, `dominio/excecao/{DocumentoGrandeDemaisException, FalhaEmbeddingException, FalhaEmbeddingTransitoriaException}`, `aplicacao/{IndexarDocumentoService, PoliticaNovasTentativas, PublicarDocumentoService, RecolherDocumentosInterrompidosService}`, `aplicacao/porta/RepositorioVetorialPort`, `infraestrutura/vetorial/{PgVectorRepositorioAdapter, TradutorFalhasEmbedding}`, `infraestrutura/config/IndexacaoConfig`, `infraestrutura/persistencia/LimpezaDocumentosInterrompidos`
- `test`: `dominio/{DivisorTextoTest, IdTrechoEVigenciaTest}`, `aplicacao/{PoliticaNovasTentativasTest, IndexarDocumentoServiceTest, RecolherDocumentosInterrompidosServiceTest, PublicacaoAtomicaIT}`, `api/rest/CadastroCompletoIT`, `infraestrutura/vetorial/{PgVectorStoreComportamentoIT, PgVectorRepositorioAdapterIT, TradutorFalhasEmbeddingTest}`, `infraestrutura/persistencia/{LimpezaDocumentosInterrompidosTest, LimpezaDocumentosInterrompidosIT}`, `suporte/{EmbeddingModelFalso, EmbeddingFalsoConfig}`

#### Arquivos modificados

- `api-conhecimento/pom.xml`: starter do pgvector
- `application.yml`: `spring.ai.vectorstore.pgvector`, `coti.conhecimento.divisao` e `coti.conhecimento.embeddings`
- `PropriedadesConhecimento`: componentes `Divisao` e `Embeddings`
- `CadastrarConhecimentoService` (reescrito), `RepositorioDocumentoPort` e `DocumentoJdbcRepositorio` (`buscarProcessandoAnterioresA`), `ManipuladorExcecoesConhecimento` (413 por excesso de trechos; 503 com `conhecimentoId` e `Retry-After`)
- Testes: `TesteWeb` (importa o dublê de embeddings), `ConhecimentoControllerTest`, `CadastrarConhecimentoServiceTest`, `RepositorioDocumentoEmMemoria`, `LimiteTamanhoCorpoFilterTest`, `AnalisadorConteudoRegexTest`
- Removidos: `IndexacaoDocumentoPort`, `IndexadorNaoImplementado` e `CadastroSemIndexadorIT` (o indexador provisório da Fase 3)
- Specs e docs: `05-tasks.md`, `04-plan.md` (§3, confirmações do spike), `03-architecture.md` (§6.3 e ADR-13), `README.md` e este arquivo

#### Comandos executados e resultados

| Comando | Resultado |
|---|---|
| `./mvnw -B -pl api-conhecimento test -Dtest='PgVectorRepositorioAdapterIT,PublicacaoAtomicaIT,CadastroCompletoIT,LimpezaDocumentosInterrompidosIT,ConhecimentoControllerTest'` | **BUILD SUCCESS**, 56 testes, 0 falhas |
| `./mvnw -B verify` (raiz), 1ª execução | **FALHOU**: `CadastroSemIndexadorIT` esperava 503 e recebeu 201. Causa: o `.class` do teste removido continuava em `target/test-classes` |
| `./mvnw -B clean verify` (raiz) | **BUILD SUCCESS**: `api-conhecimento` **350 testes, 0 falhas, 1 ignorado**; `api-atendimentos` 61 testes, 0 falhas, 1 ignorado |
| `OPENAI_API_KEY=<chave falsa> docker compose up -d --build postgres api-conhecimento` | Contêiner `healthy`; log: "PG VectorStore schema validation" concluída com o papel DML-only; `/actuator/health/readiness` = `UP` |
| `docker compose down` | Stack derrubada (volume do banco preservado) |

#### Testes automatizados (resumo)

| Área | Cobertura |
|---|---|
| `DivisorTexto` (27) | limite de tamanho, sobreposição em começo de palavra, corte por parágrafo, frase, linha, espaço e meio de palavra, texto curto, último trecho curto preservado, determinismo, acentos, limite de 500 trechos |
| `IdTrecho` e vigência (11) | UUID v5 estável, distinto por documento e posição; conversão de data para `AAAAMMDD`, `99991231`, vigência na fronteira |
| Retry e lotes (15) | só falha transitória é repetida, número máximo de tentativas, backoff com variação, falha definitiva sem nova tentativa, divisão em lotes de `tamanhoLote` |
| `TradutorFalhasEmbedding` (8) | 429, 5xx, 408 e erro de E/S → transitória; 401, 403 e demais → definitiva, sem a chave no log |
| `CadastrarConhecimentoService` (16) | ordem das chamadas, rejeições sem tocar no provedor, 413 antes de gravar, compensação (inclusive quando a publicação falha), exceções suprimidas preservadas |
| Banco real | spike do `PgVectorStore` (8): tipo `json`, upsert por id, `delete` por filtro, só o texto vira embedding, 1536 dimensões, lotes grandes; adaptador (11): ids, metadados, vigência, idempotência, promoção só do documento certo, remoção, tradução de falhas sem linhas gravadas; publicação atômica (5): falha na atualização do documento ou contagem divergente desfaz a promoção |
| `CadastroCompletoIT` (16) | 201 com trechos, metadados e vetores reais; lotes de 2; 429 e 5xx seguidos de sucesso; 401 sem nova tentativa; 429 permanente (3 chamadas); falha definitiva no 2º lote sem trechos restantes; 413, 409, injeção e CPF sem chamar o provedor; 401 sem chave |
| Limpeza (2 + 5) | falha na limpeza não impede a subida; recolhe antigos com e sem trechos, preserva recentes e publicados, idempotente |

**Teste ignorado (1 por serviço):** a variante "ausente" de `OPENAI_API_KEY` no `InicializacaoSemVariavelTest`, porque a variável existe no ambiente deste computador (igual às fases anteriores).

**Não executado:** cadastro real contra a OpenAI e `POST` na stack Docker. A chave administrativa só existe no `.env`, que não li, e um cadastro válido enviaria texto à OpenAI. O caminho está coberto com o dublê do modelo e PostgreSQL real; falta apenas a prova com a OpenAI de verdade (ver pendências).

#### Decisões e desvios

- **Publicação sem `PublicacaoPort`/`PublicacaoJdbcAdapter` (desvio da T034).** `PublicarDocumentoService` abre a transação na camada de aplicação e usa `RepositorioVetorialPort.promoverTrechos` e `RepositorioDocumentoPort.atualizarEstado`. Descartado o adaptador JDBC separado: duplicaria o acesso à `vector_store`. Se a contagem de trechos promovidos diferir da gravada, a transação desfaz (`IllegalStateException`).
- **413 antes de gravar (desvio do diagrama de §6.2).** A divisão acontece antes do `INSERT`; acima de 500 trechos nada é salvo, o que casa com o contrato (RF02) e evita um documento em `ERRO_PROCESSAMENTO` sem causa útil para o gestor.
- **Último trecho curto nunca é descartado.** `tamanhoMinimo` só limita a posição do corte, para não perder o final do texto.
- **Retry com `RetryTemplate` do Spring Framework 7**, sem dependência nova; `max-retries: 0` no SDK.
- **`IndexacaoDocumentoPort` removida.** A porta provisória da Fase 3 deixou de ter razão de existir; `CadastrarConhecimentoService` agora usa `IndexarDocumentoService` e `PublicarDocumentoService`.
- **`PROCESSANDO` continua invisível** na API e na busca (metadado `status` nos trechos).
- **Arquivos `.class` obsoletos.** Ao remover um teste, rode `./mvnw clean verify` uma vez; senão o Surefire executa o `.class` antigo em `target/test-classes`.
- **Erro de causa no 503:** `BASE_CONHECIMENTO_INDISPONIVEL` quando a causa é o banco, `MODELO_INDISPONIVEL` nos demais casos.

#### Pendências e riscos

- **Sem prova com a OpenAI real.** Ainda não houve um cadastro de ponta a ponta com chave válida. Fazer na T067 (documentos reais da COTI), com a chave do `.env`.
- **Segurança padrão do Spring:** na subida a `api-conhecimento` registra "Using generated security password" (o `UserDetailsService` em memória do Spring Boot). O valor é uma senha aleatória sem uso, porque a autenticação é por `X-Api-Key`, mas imprimir credencial no log viola a regra de logs sem segredo. Fora do escopo da Fase 4; sugestão: excluir `UserDetailsServiceAutoConfiguration` (ou declarar um `UserDetailsService` vazio) na Fase 8. Avaliar também a `api-atendimentos`.
- **Sem avaliação de qualidade da divisão.** Tamanho e sobreposição (1.500 / 200) seguem a skill; a nota de recuperação só existe com o eval da Fase 8 (T069/T070). Não há afirmação de qualidade sem medir.
- **Cadastro síncrono.** Um documento de 500 trechos faz ~5 chamadas à OpenAI dentro da requisição HTTP; o gestor espera. Aceitável no MVP; assíncrono fica fora do escopo.
- **Limpeza na subida só roda no start.** Um cadastro interrompido com a aplicação no ar (sem reiniciar) permanece `PROCESSANDO` até a próxima subida. Raro, pois a compensação cobre falhas; avaliar tarefa agendada se aparecer.
- Itens antigos: allowlists de e-mail e telefone institucionais vazias, `Retry-After` em 429 (Fase 8), chave MCP (Fase 5), scanner de segredos (T066), documentos reais (T067).

#### Próxima fase

**Fase 5 — Busca vetorial e MCP Server** (T037–T043). A primeira tarefa é a **T037**, um spike (`ScoreCossenoIT`) para confirmar como o Spring AI 2.0.1 devolve a similaridade antes de fixar o limiar de relevância.

---

---

### Fase 5 — Busca vetorial e MCP Server

**Status:** concluída. **Data:** 2026-10-27. **Tarefas:** T037 a T043 (7 de 7).

#### O que foi entregue

| Tarefa | Resultado |
|---|---|
| T037 | Spike `ScoreCossenoIT` (12 testes): o score é o cosseno (`1 - distância`), o limiar padrão 0 mantém tudo em [0, 1], `gte` sobre inteiro filtra a vigência dentro da busca, e o **risco do HNSW foi medido** (ver decisões). Resultados em `04-plan.md` §3 |
| T038 | `BuscarConhecimentoService`: valida (pergunta 3 a 1.000 após `strip`, limite 1 a 10, padrão 5), busca com tempo limite de 5 s, ordena, corta 2.000 por trecho e 8.000 no total (sai inteiro o de menor similaridade). Porta `BuscaVetorialPort` e `PgVectorBuscaAdapter` (filtro de `PUBLICADO`, modelo e vigência dentro da consulta; falhas traduzidas para `ModeloIndisponivelException` e `BaseConhecimentoIndisponivelException`). Limites em `coti.conhecimento.busca` |
| T039 | `POST /api/conhecimentos/diagnostico/busca` (`DiagnosticoBuscaController`), com `BuscaDiagnosticoRequest` e `BuscaDiagnosticoResponse` (`record`), chave administrativa, 400 e 503 pelo manipulador de exceções |
| T040 | Servidor MCP (`spring-ai-starter-mcp-server-webmvc`, `STREAMABLE`, só a capacidade de ferramentas) e `BuscarConhecimentoTool` com as anotações de somente leitura; `ResultadoBusca`, `TrechoResultado`, `ErroFerramenta` e `ErroFerramentaException` |
| T041 | Cadeia de segurança de `/mcp` com `Authorization: Bearer` (`BearerAuthFilter`) e nova variável `CONHECIMENTO_MCP_API_KEY`; a chave administrativa e a de monitoramento recebem 401 |
| T042 | `BuscarConhecimentoToolContratoIT` (36 testes) com cliente MCP real em porta aleatória: `tools/list`, `tools/call`, argumentos inválidos, banco e embedding indisponíveis, tempo limite, erro inesperado, credencial |
| T043 | `DiagnosticoIgualMcpIT` (6 testes): mesma pergunta, mesmos trechos, ordem e similaridades pelo diagnóstico e pelo MCP |

#### Arquivos criados (em `api-conhecimento/src`)

- `main`: `dominio/{TrechoEncontrado, LimitesBusca}`, `dominio/excecao/{ArgumentosInvalidosException, BaseConhecimentoIndisponivelException, ModeloIndisponivelException}`, `aplicacao/{BuscarConhecimentoService, ResultadoDaBusca}`, `aplicacao/porta/BuscaVetorialPort`, `infraestrutura/vetorial/PgVectorBuscaAdapter`, `infraestrutura/config/BuscaConfig`, `infraestrutura/seguranca/BearerAuthFilter`, `api/mcp/{BuscarConhecimentoTool, ResultadoBusca, TrechoResultado, ErroFerramenta, ErroFerramentaException}`, `api/rest/DiagnosticoBuscaController`, `api/rest/dto/{BuscaDiagnosticoRequest, BuscaDiagnosticoResponse}`
- `test`: `aplicacao/{BuscarConhecimentoServiceTest, BuscaVetorialIT}`, `infraestrutura/vetorial/{ScoreCossenoIT, PgVectorBuscaAdapterTest}`, `infraestrutura/seguranca/SegurancaMcpIT`, `api/rest/DiagnosticoBuscaControllerTest`, `api/mcp/{BuscarConhecimentoToolTest, BuscarConhecimentoToolContratoIT, DiagnosticoIgualMcpIT}`, `suporte/{TrechosDeTeste, TesteServidor, ClienteMcpDeTeste}`

#### Arquivos modificados

- `api-conhecimento/pom.xml`: starter do servidor MCP
- `application.yml`: `spring.ai.mcp.server.*`, `coti.conhecimento.busca.*`, `seguranca.mcp-api-key` e `hikari.connection-init-sql` com o scan iterativo do HNSW
- `PropriedadesConhecimento` (`Busca` e `Seguranca.mcpApiKey`), `SecurityConfig` (cadeia `/mcp`, papel `SERVICO_ATENDIMENTO`), `ManipuladorExcecoesConhecimento` (400 e 503 da busca)
- `docker-compose.yml` e `.env.example`: `CONHECIMENTO_MCP_API_KEY` na `api-conhecimento`
- Testes: `TesteWeb`, `ChavesTeste`, `InicializacaoSemVariavelTest` (a nova chave), `AnalisadorConteudoRegexTest` e `LimiteTamanhoCorpoFilterTest` (construtor)
- Docs: `05-tasks.md`, `04-plan.md` (§3, confirmações dos spikes T037 e do MCP Server), `03-architecture.md` (§7 e ADR-19, ADR-20), `06-api-contracts.md` (esclarecimentos, versão 1.0.0 revisada), `README.md`, skills `rag-pgvector`, `mcp-integration` e `testing`, e este arquivo

#### Comandos executados e resultados

| Comando | Resultado |
|---|---|
| `./mvnw -B clean verify` (raiz), 1ª execução completa | **FALHOU** (3 testes): `SegurancaMcpIT` não reiniciava o dublê de embeddings, e dois testes do `ScoreCossenoIT` perdiam resultados na suíte completa. Segunda causa investigada em "Decisões" |
| `./mvnw -B clean verify` (raiz), final | **BUILD SUCCESS**: `api-conhecimento` **518 testes, 0 falhas, 1 ignorado** (eram 350); `api-atendimentos` 61 testes, 0 falhas, 1 ignorado |
| `OPENAI_API_KEY=<falsa> CONHECIMENTO_MCP_API_KEY=<gerada> docker compose up -d --build postgres api-conhecimento` | Contêiner `healthy`; log: "PG VectorStore schema validation successful" com o papel só DML, "Registered tools: 1"; a subida com o `connection-init-sql` aplicado (se o SQL falhasse, o pool Hikari derrubaria a subida) |
| `POST /mcp` sem credencial; com `X-Api-Key` | `401` e `401` |
| `initialize` com Bearer, depois `notifications/initialized` e `tools/list` | `serverInfo` = `coti-conhecimento` 1.0.0; `202`; `tools/list` com `buscar_conhecimento`, título, descrição e schemas |
| `docker compose down` | Stack derrubada |

#### Testes automatizados (resumo)

| Área | Cobertura |
|---|---|
| Spike T037 (12) | score = cosseno, faixa [0, 1], limiar inclusivo, `topK`, `gte` de vigência, filtro composto, valor com aspas, HNSW com 300 trechos não publicados, índice forçado com 400 linhas apagadas (com e sem scan iterativo), configuração da conexão |
| `BuscarConhecimentoServiceTest` (35) | validação (nula, vazia, curta, longa, limites 3 e 1.000, `strip`), limite 0/11 e extremos, padrão 5, consulta com modelo, limiar e data, ordem, sem score, cortes 2.000 e 8.000 (inclusive 12.000 e soma exata), lista vazia, tempo limite com interrupção da tarefa, propagação de falhas, limites incoerentes |
| `PgVectorBuscaAdapterTest` (11) | requisição (pergunta, `topK`, limiar), filtro com status, modelo e vigência, pergunta com aspas e operadores, conversão sem metadados internos, score acima de 1, 4 tipos de falha do provedor, falha do banco, metadado ausente |
| `BuscaVetorialIT` (15, banco real) | não publicado, outro modelo e vencido nunca voltam; vigência inclusiva; limiar; ordem; limite e padrão; campos do contrato; cortes; documento cadastrado de verdade aparece pelo próprio texto; documento em revisão não é indexado; tempo limite; falha do provedor |
| `DiagnosticoBuscaControllerTest` (22) | 401 (sem chave, errada, monitoramento), 400 (campos, tamanhos, limite, tipo, campo desconhecido, sem corpo), 415, 200 com todos os campos, ordem, sem campos internos, lista vazia, não publicados, pergunta ausente da resposta, 503 com `Retry-After` |
| `SegurancaMcpIT` (19) | sem credencial, chave errada, administrativa e de monitoramento como Bearer, `X-Api-Key`, esquemas errados, `bearer` em minúsculas, outros métodos HTTP, subcaminhos, corpo do 401 sem chave, a chave de serviço não abre REST nem Actuator |
| `BuscarConhecimentoToolContratoIT` (36, cliente MCP real) | servidor e capacidades, nome, título, descrição, anotações, `inputSchema` e `outputSchema`, as **divergências conhecidas** do schema, `structuredContent` e texto equivalentes, padrão 5, cortes, não publicados, argumentos inválidos (JSON `ErroFerramenta`), violação de schema do SDK, banco, embedding, tempo limite, erro interno sem detalhe, sessão que sobrevive a falha, ferramenta inexistente, credenciais |
| `DiagnosticoIgualMcpIT` (6) | mesmo resultado para os limites de 1 a 10, lista vazia, corte de 8.000, outra pergunta com espaços |
| `BuscarConhecimentoToolTest` (10) | delegação e conversão, códigos de erro, mensagem do JSON com causa vazia, anotações do método sem exceção checada |

**Teste ignorado (1 por serviço):** a variante "ausente" de `OPENAI_API_KEY` no `InicializacaoSemVariavelTest`, porque a variável existe no ambiente deste computador.

**Não executado:** `tools/call` e `POST` de diagnóstico na stack Docker, porque a busca gera o embedding da pergunta e enviaria texto à OpenAI; nesse caso as chaves do `.env` seriam necessárias. O caminho está coberto com o dublê do modelo e PostgreSQL real. Falta a prova com a OpenAI de verdade (T067).

#### Decisões e desvios

- **Risco do HNSW medido e mitigado (ADR-19).** Com o índice forçado e 400 linhas apagadas, a busca devolvia vazio mesmo havendo 3 trechos válidos, porque o índice guarda entradas mortas até o `VACUUM` e o filtro vem depois do scan. `hnsw.iterative_scan = strict_order` em toda conexão corrige o caso (0 → 3). Descartei `ALTER ROLE ... SET` (privilégio que o dono não tem) e `SET LOCAL` por busca (transação em volta de cada consulta). **Limite:** o HNSW continua aproximado; depois de muita remoção sem `VACUUM` o grafo degrada. Em produção o autovacuum cuida disso; o script de remoção de documentos deve rodar `VACUUM`. A nota de recuperação só sai do eval da Fase 8.
- **Testes de busca zeram a tabela com `TRUNCATE` como dono** (`TrechosDeTeste.zerar()`), porque `DELETE` deixa entradas mortas e a busca aproximada passava a depender da ordem dos testes (foi o que quebrou a primeira suíte completa). O papel da aplicação continua sem `TRUNCATE`.
- **Tempo limite de 5 s aplicado na aplicação, sobre a busca inteira** (embedding da pergunta mais consulta), em thread virtual com `Future.get(timeout)`; no estouro vira `MODELO_INDISPONIVEL` e a tarefa é interrompida. O embedding é o trecho lento; um banco lento também estouraria, e ainda assim é a resposta certa para quem espera.
- **Texto de erro do MCP (desvio da forma esperada).** O Spring AI 2.0.1 monta `mensagem + quebra de linha + causa raiz`, então o JSON saía duplicado. `ErroFerramentaException` leva uma causa de mensagem vazia e o texto fica sendo o JSON mais uma quebra de linha. O contrato foi esclarecido (§4.5) e o teste de contrato trava o formato.
- **Divergências do schema publicado.** `@McpToolParam` não gera `minLength`, `maxLength`, `minimum`, `maximum`, `default` nem `additionalProperties:false`, e o schema de saída não tem `maxItems` nem `maxLength` e não aceita `similaridade` nula. O contrato foi esclarecido (§4.2) e as validações estão no caso de uso. Tipo errado ou campo ausente é barrado pelo SDK com texto próprio (não é `ErroFerramenta`); o cliente da Fase 6 trata todo `isError` como falha.
- **Bug achado pelo cliente real:** sem gravar o contexto de segurança na requisição, o redespacho assíncrono do SSE perdia a identidade e o log enchia de `AccessDenied ... response already committed`. O `BearerAuthFilter` agora usa `RequestAttributeSecurityContextRepository`; o log ficou limpo.
- **Terceira credencial (ADR-20):** `CONHECIMENTO_MCP_API_KEY`, em cadeia própria. Descartei reaproveitar a chave administrativa e o módulo de segurança MCP da comunidade (dependência nova).
- **Dependência nova:** `spring-ai-starter-mcp-server-webmvc`, do BOM do Spring AI 2.0.1, já prevista na arquitetura (§13 como definida pelo projeto) e no plano; traz o SDK `io.modelcontextprotocol.sdk:mcp` 2.0.0. Não conferi a licença nesta sessão; os dois são projetos do ecossistema Spring e do Model Context Protocol, e a licença deve ser confirmada no `pom` publicado antes da produção.
- **`BuscarConhecimentoService`** é `final`, sem transação, e fecha o executor de threads virtuais ao parar (`AutoCloseable`).

#### Pendências e riscos

- **Variável nova no `.env`.** Foi criada `CONHECIMENTO_MCP_API_KEY` (já constava no `.env.example`, mas o Compose agora a exige). Eu não li nem alterei o seu `.env`: se a variável não estiver lá, `docker compose up` falha pedindo por ela. Gere um valor aleatório e coloque no `.env`.
- **Limite de 10 requisições por minuto do diagnóstico (§3.5)** e o `429` ficam para a Fase 8 (T062).
- **`UserDetailsServiceAutoConfiguration`:** continua imprimindo a senha gerada pelo Spring no log da subida (pendência da Fase 4, ainda aberta; sugestão: excluir a autoconfiguração na Fase 8).
- **Sem prova com a OpenAI real**, nem de qualidade da recuperação: o limiar 0,70 e `k` = 5 são valores iniciais; o eval da Fase 8 decide.
- **`VACUUM` após remoções:** o script administrativo de remoção de documentos ainda não existe (fora do MVP por API); quando existir, precisa rodar `VACUUM vector_store`.
- **Licença** do starter MCP e do SDK a confirmar (ver decisões).
- Itens antigos: allowlists de e-mail e telefone institucionais vazias, `Retry-After` em 429, scanner de segredos (T066), documentos reais (T067).

#### Próxima fase

**Fase 6 — MCP Client e API de Atendimentos** (T044–T053). A primeira tarefa é a **T044**, um spike do cliente MCP do Spring AI 2.0.1 na `api-atendimentos` (tipo injetável do `McpSyncClient`, cabeçalhos de credencial e de trace, comportamento com a `api-conhecimento` fora do ar na subida). A chave `CONHECIMENTO_MCP_API_KEY` precisará entrar também no serviço e no Compose da `api-atendimentos`.

---

---

### Fase 6 — MCP Client e API de Atendimentos

**Status:** concluída. **Data:** 2026-10-27. **Tarefas:** T044 a T053 (10 de 10).

#### O que foi entregue

| Tarefa | Resultado |
|---|---|
| T044 | Spike do cliente MCP (Spring AI 2.0.1, SDK 2.0.0), com achados que mudaram o desenho: `initialized: false` obrigatório, não interromper o cliente, sessão perdida. Registrado em `04-plan.md` §3 |
| T045 | Domínio: `PerguntaAluno`, `TrechoConhecimento`, `Fonte`, `ResultadoAtendimento`, `RespostaGerada`, `Prazo`, `Categoria` e as exceções `BaseConhecimentoIndisponivelException`, `ModeloIndisponivelException`, `TempoEsgotadoException`, `PerguntaInvalidaException` |
| T046 | `MascaradorPort` e `MascaradorDadosPessoaisRegex` (CPF, e-mail e telefone → `[CPF]`, `[EMAIL]`, `[TELEFONE]`) |
| T047 | `PerguntarRequest`, `RespostaAtendimentoResponse`, `FonteResponse`, `AtendimentoController` e `ManipuladorExcecoesAtendimento` (503 com `Retry-After`) |
| T048 | `ServidorMcpDeTeste`: servidor MCP mínimo (JDK puro) que segue o contrato §4, com sucesso, vazio, `isError`, fora do schema, lento, 401/403/500, sem ferramenta e sessão perdida |
| T049 | Cliente MCP configurado (`SYNC`, 5 s, sem `ToolCallback`, `initialized: false`), `McpCabecalhosCustomizer` (Bearer + `traceparent`) e `McpContextoTraceCustomizer` |
| T050 | `ValidadorResultadoBusca` (rejeita o resultado inteiro) e `BaseConhecimentoMcpAdapter` (chamada determinística, limite de tempo pelo `Prazo`, novas tentativas só para falha de transporte) |
| T051 | `GeradorRespostaPort` (interface) e `ResponderPerguntaService`: mascarar → buscar → sem trechos = sem evidência; prazo → `TEMPO_ESGOTADO`; falhas → 503 |
| T052 | `ConhecimentoMcpHealthIndicator` e status agregado `DEGRADADO` (HTTP 200); liveness e readiness independentes |
| T053 | `ServicosIntegradosIT` e `ChaveMcpErradaIT`: a api-atendimentos contra o JAR real da api-conhecimento, com PostgreSQL e pgvector reais e uma OpenAI falsa só de embeddings |

#### Arquivos criados (em `api-atendimentos/src`)

- `main`: `dominio/{Categoria, PerguntaAluno, TrechoConhecimento, Fonte, ResultadoAtendimento, RespostaGerada, Prazo}`, `dominio/excecao/{BaseConhecimentoIndisponivelException, ModeloIndisponivelException, TempoEsgotadoException, PerguntaInvalidaException}`, `aplicacao/{ResponderPerguntaService, porta/BaseConhecimentoPort, porta/GeradorRespostaPort, porta/MascaradorPort}`, `infraestrutura/mcp/{BaseConhecimentoMcpAdapter, ValidadorResultadoBusca, McpCabecalhosCustomizer, McpContextoTraceCustomizer, ConhecimentoMcpHealthIndicator}`, `infraestrutura/privacidade/MascaradorDadosPessoaisRegex`, `infraestrutura/ia/GeradorRespostaNaoImplementado` (provisório), `infraestrutura/config/AtendimentoConfig`, `api/rest/{AtendimentoController, ManipuladorExcecoesAtendimento}`, `api/rest/dto/{PerguntarRequest, RespostaAtendimentoResponse, FonteResponse}`
- `test`: `dominio/{PrazoTest, PerguntaAlunoTest}`, `aplicacao/ResponderPerguntaServiceTest`, `infraestrutura/privacidade/MascaradorDadosPessoaisRegexTest`, `infraestrutura/mcp/{ValidadorResultadoBuscaTest, McpCabecalhosCustomizerTest, ConhecimentoMcpHealthIndicatorTest, BaseConhecimentoMcpAdapterIT, HealthDegradadoIT, SubidaSemConhecimentoIT}`, `api/rest/AtendimentoControllerTest`, `integracao/{ServicosIntegradosIT, ChaveMcpErradaIT}`, `suporte/{ServidorMcpDeTeste, ServidorMcpDeTesteTest, TesteComServidorMcp, OpenAiFalsa, AmbienteIntegrado}`

#### Arquivos modificados

- `api-atendimentos/pom.xml`: starter do cliente MCP, `testcontainers-postgresql` (escopo de teste) e o Surefire executando também `*IT`
- `api-atendimentos/src/main/resources/application.yml`: cliente MCP, `coti.atendimentos.{seguranca.mcp-api-key, conhecimento, mensagens, busca, resiliencia}` e o status `DEGRADADO` do Actuator; `PropriedadesAtendimentos` ampliada
- `api-conhecimento`: `PgVectorRepositorioAdapter` (formatador de conteúdo só com o texto), `application.yml` (`metadata-mode: NONE`) e o novo `PgVectorRepositorioAdapterFormatoTest` (correção descrita em "Decisões")
- Testes existentes ajustados: `TesteWeb`, `ChavesTeste`, `InicializacaoSemVariavelTest`, `LimiteTamanhoCorpoFilterTest`, `ActuatorAcessoTest`, `SegurancaTest` (a rota de perguntas agora existe) e `ManipuladorErrosRestTest` (usa o controller real com o caso de uso em dublê)
- `docker-compose.yml`, `.env.example`, `README.md`, `specs/{03, 04, 05, 06, 08}`, skills `mcp-integration`, `rag-pgvector` e `testing`

#### Comandos executados e resultados

| Comando | Resultado |
|---|---|
| `./mvnw -B clean verify` (raiz), final | **BUILD SUCCESS**: `api-conhecimento` **524 testes** (eram 518), 0 falhas, 1 ignorado; `api-atendimentos` **240 testes** (eram 61), 0 falhas, 1 ignorado |
| `./mvnw -B -pl api-atendimentos test -Dtest='BaseConhecimentoMcpAdapterIT'`, 1ª execução | **FALHOU** (19 de 22): interromper a chamada durante a conexão inicial travou o cliente MCP. Corrigido (ver "Decisões") |
| `./mvnw -B -pl api-atendimentos test -Dtest='ServicosIntegradosIT,ChaveMcpErradaIT'`, 1ª execução | **FALHOU** (1 de 5): a pergunta com evidência voltou "sem evidência". Causa: os metadados entravam no texto do embedding (ver "Decisões"). Corrigido |
| `docker compose up -d --build` (os dois serviços, chaves falsas, chave MCP gerada) | Os dois contêineres `healthy`; saúde pública `UP`; `400` para pergunta curta |
| `docker stop coti-api-conhecimento` | Saúde `DEGRADADO` com HTTP `200`; liveness e readiness `UP`; pergunta devolveu `503` com `Retry-After: 30` e `X-Correlation-Id` |
| `docker start coti-api-conhecimento` | A api-atendimentos voltou a `UP` sozinha, sem reinício (levou cerca de 18 s: cache de saúde de 10 s mais uma verificação que falhou por causa da sessão MCP antiga) |
| `docker compose down` | Stack derrubada |

#### Testes automatizados (resumo)

| Área | Cobertura |
|---|---|
| Domínio | `PrazoTest` (7: restante, expiração, `limitar`, relógio manual), `PerguntaAlunoTest` (9) |
| `MascaradorDadosPessoaisRegexTest` (23) | CPF com e sem pontuação, dígito verificador, e-mail, 8 formatos de telefone, protocolo de 10 dígitos preservado, preços e datas preservados, idempotência |
| `ValidadorResultadoBuscaTest` (36) | resultado válido, vazio, 10 trechos, cada campo obrigatório ausente, UUID, categoria, similaridade fora de 0 a 1, `total` divergente, `id` repetido, mais de 10, texto de 2.000+, `isError` sem vazar o texto, mensagens sem conteúdo |
| `ResponderPerguntaServiceTest` (13) | texto mascarado é o único que sai, sem trechos o gerador nunca é chamado, fontes copiadas dos citados, prazo único no fluxo, falhas sem resposta parcial, prazo vencendo no meio |
| `BaseConhecimentoMcpAdapterIT` (23, servidor MCP de teste) | chamada e conversão, Bearer e `traceparent`, 9 formas de resultado inválido sem nova tentativa, 401/403, 500 e lentidão com 3 tentativas, timeout seguido de sucesso, prazo curto e expirado, sessão perdida |
| `AtendimentoControllerTest` (20) | 400 sem chamar o caso de uso (curta, longa, campo extra, ausente, após `strip`), formato 200 do contrato, `atendimentoId` = `X-Correlation-Id`, sem evidência, similaridade nula, 503 de cada falha com `Retry-After`, 500 genérico |
| Saúde | `HealthDegradadoIT` (9), `ConhecimentoMcpHealthIndicatorTest` (6: cache, ferramenta ausente, falha sem vazar mensagem), `SubidaSemConhecimentoIT` (4: sobe sem a base, `DEGRADADO`, 503, conexão recusada) |
| Cabeçalhos | `McpCabecalhosCustomizerTest` (10) |
| Entre serviços reais | `ServicosIntegradosIT` (3: sem evidência 200, o CPF nunca chega à OpenAI, pergunta com evidência chega ao gerador) e `ChaveMcpErradaIT` (2: 401 real da api-conhecimento, 503 ao aluno) |
| Servidor de teste | `ServidorMcpDeTesteTest` (6) |

**Teste ignorado (1 por serviço):** a variante "ausente" de `OPENAI_API_KEY` no `InicializacaoSemVariavelTest`, porque a variável existe no ambiente deste computador.

#### Decisões e desvios

- **`initialized: false` é obrigatório (ADR-21).** Com o padrão a api-atendimentos não sobe com a api-conhecimento desligada, o que viola o RNF07. Com `false` ela sobe, conecta na primeira chamada e se reabre sozinha.
- **A thread do cliente MCP nunca é interrompida.** `Future.cancel(true)` durante a conexão inicial travou o cliente para sempre (19 dos 22 testes do adaptador falharam). O adaptador para de esperar e deixa o `request-timeout` do SDK terminar a chamada.
- **Novas tentativas valem para qualquer falha de transporte** (desvio do contrato, que dizia só timeout e conexão): depois de a api-conhecimento reiniciar, o SDK falha a primeira chamada com uma `RuntimeException` sem tipo ("MCP session with server terminated") e se recupera na seguinte; sem repetir, o primeiro aluno após cada reinício veria 503. Credencial recusada, `isError` e resultado fora do contrato continuam sem repetição. Contrato esclarecido (§6.5).
- **Achado na T053, corrigido na api-conhecimento (Fase 4):** o modelo de embedding da OpenAI manda os **metadados** junto com o texto do trecho (padrão `EMBED`), e mesmo com `metadata-mode: NONE` sobra um prefixo `"\n\n"`. O spike T031 tinha concluído o contrário porque o dublê lia direto `getText()`. Corrigi com `metadata-mode: NONE` e um `ContentFormatter` por `Document` que devolve só o texto (ADR-22), com teste unitário e prova ponta a ponta. Nada em produção precisa ser reindexado.
- **Componente `conhecimentoMcp` reporta `DEGRADADO`, não `DOWN`** (desvio do exemplo do contrato §5.2). O Actuator agrega pelo pior status; com um componente `DOWN` o serviço inteiro ficaria `DOWN` (503). O resultado fica em cache por 10 s para o endpoint público não virar uma chamada ao MCP por requisição.
- **Resposta sem evidência = mensagem + espaço + canal humano**, os dois de configuração (`ATENDIMENTO_MENSAGEM_SEM_EVIDENCIA` e `ATENDIMENTO_CANAL_HUMANO`, agora obrigatórias).
- **Gerador provisório.** `GeradorRespostaNaoImplementado` sempre lança `ModeloIndisponivelException`; uma pergunta que encontra trechos termina em `503 MODELO_INDISPONIVEL` até a Fase 7. `ServicosIntegradosIT` documenta isso e passa a esperar 200 com a fonte quando o gerador existir. A assinatura de `GeradorRespostaPort` (pergunta mascarada, trechos, prazo) pode mudar na T054 para receber o `ContextoRag`.
- **O filtro de limiar na `api-atendimentos` (contrato §6.3, passo 1) fica na T054** (`MontadorContextoRag`), junto com sanitização, cortes e rótulos `T<n>`.
- **Pergunta mascarada acima de 1.000 caracteres é truncada** (um e-mail curto vira `[EMAIL]`, mais longo) para o servidor não recusá-la.
- **Dependência de teste:** `testcontainers-postgresql` na `api-atendimentos`, só no escopo de teste (mesmo artefato já aprovado para a `api-conhecimento`; não traz o driver JDBC, e o `IsolamentoDeDependenciasTest` continua passando). O Surefire do módulo passou a executar `*IT`.
- **Tempo e variáveis:** `CONHECIMENTO_MCP_TIMEOUT` alimenta ao mesmo tempo o `request-timeout` do SDK e o limite do adaptador.

#### Pendências e riscos

- **Variáveis novas no `.env`.** O Compose agora exige, para a `api-atendimentos`, `ATENDIMENTO_MENSAGEM_SEM_EVIDENCIA` e `ATENDIMENTO_CANAL_HUMANO` (além de `CONHECIMENTO_MCP_API_KEY`, da Fase 5). Eu não li nem alterei o seu `.env`; sem elas `docker compose up` falha pedindo cada uma pelo nome.
- **Uma pergunta genérica chegou à OpenAI na validação no Docker.** Ao testar a degradação, enviei "Quais sao as modalidades?" à stack; a api-conhecimento tentou gerar o embedding com a chave **falsa** e a OpenAI recusou (401). Nenhuma chave real, dado pessoal nem documento saiu; registro por transparência.
- **Limite de requisições** (10 por minuto por IP no `perguntar`) e o `429` continuam para a Fase 8 (T062). Hoje a rota pública não tem limite.
- **Recuperação após reinício da api-conhecimento** leva até cerca de 18 s no health (cache de 10 s mais uma verificação com a sessão antiga); as perguntas se recuperam sozinhas pela nova tentativa.
- **Primeira chamada** abre a sessão MCP (cerca de 300 ms); cabe no limite de 5 s.
- **Sem prova com a OpenAI real**, tanto de embedding quanto de chat (T067 e Fase 7).
- **`UserDetailsServiceAutoConfiguration`** imprime a senha gerada no log da subida na `api-conhecimento` (pendência da Fase 4); não verifiquei se a `api-atendimentos` também. Fica para a Fase 8.
- Itens antigos: allowlists de e-mail e telefone institucionais vazias, scanner de segredos (T066), documentos reais (T067), licença do starter MCP.

#### Pendências resolvidas antes da Fase 7 (2026-10-27)

| Pendência | Como foi resolvida |
|---|---|
| Limite de requisições (T062) | `BaldeTokens` (balde de fichas em memória, ADR-12) e `LimiteRequisicoesFilter` nos dois serviços. `api-atendimentos`: 10 perguntas por minuto por IP da conexão (não confia em `X-Forwarded-For`). `api-conhecimento`: cadastro 30 e diagnóstico 10 por minuto por credencial, baldes separados, logo depois da autenticação (sem credencial válida não gasta ficha). 429 `LIMITE_REQUISICOES` com `Retry-After` no formato do contrato. Por instância; valer no conjunto exige armazenamento compartilhado (pré-produção). Testes: `BaldeTokensTest` nos dois serviços, `LimiteRequisicoesIT` nos dois |
| Senha gerada no log da subida | `UserDetailsServiceAutoConfiguration` excluída nos dois serviços (a autenticação é por chave em filtros próprios); teste de que não existe `UserDetailsService`. Antes, a `api-conhecimento` imprimia a senha gerada pelo Spring no log |
| Saúde demorando a voltar a `UP` | O resultado ruim do `conhecimentoMcp` fica em cache por 2 s (o bom, 10 s). Antes, após o reinício da `api-conhecimento` a volta levava cerca de 18 s |
| Licença do starter MCP e do SDK | Conferida nos POMs publicados: `spring-ai-starter-mcp-*` e `spring-ai-mcp*` em Apache 2.0; SDK `io.modelcontextprotocol.sdk:mcp`, `mcp-core` e `mcp-json-jackson3` 2.0.0 em MIT |
| Remoção de documento e `VACUUM` | `scripts/remover-conhecimento.sh <id> [--sim]`: remove o registro e os trechos numa transação com o papel dono e roda `VACUUM (ANALYZE) vector_store`. Testado contra o PostgreSQL do Compose com um id inexistente (0 removidos, `VACUUM` concluído); id fora do formato UUID é recusado antes de qualquer SQL |

Continuam abertas por dependerem de você: aprovar a ferramenta do verificador de segredos (T066), documentos reais (T067), e-mails e telefones institucionais, e um teste com a OpenAI real. Suíte completa após estas correções: `api-conhecimento` 539 testes e `api-atendimentos` 256, 0 falhas, 1 ignorado em cada.

#### Próxima fase

**Fase 7 — Integração com OpenAI e montagem do contexto RAG** (T054–T061). A primeira tarefa é a **T054**: `ContextoRag`, `ReferenciaTrecho` e `MontadorContextoRag` (filtro de limiar, ordenação, sanitização, cortes de 2.000 e 8.000 caracteres, máximo de 5 trechos e rótulos `T1`, `T2`...).

---

### Fase 7 — Integração com OpenAI e montagem do contexto RAG

**Status:** concluída. **Data:** 2026-10-27. **Tarefas:** T054 a T061 (8 de 8).

#### O que foi entregue

| Tarefa | Resultado |
|---|---|
| T054 | `ContextoRag`, `ReferenciaTrecho`, `MontadorContextoRag`: limiar 0,70, ordem por similaridade (nula no fim), sanitização (controle, delimitadores forjados `‹ ›`, aspas), cortes de 2.000 por trecho e 8.000 no total (sai o trecho inteiro de menor similaridade), máximo de 5, rótulos `T1..Tn` |
| T055 | `prompts/responder-pergunta.system.v1.st` e `.user.v1.st` (parâmetros `trechos`, `pergunta`, `aviso`), versão por configuração (`coti.atendimentos.prompt.versao`) |
| T056 | `RespostaModelo`, `ChatClientConfig` e `OpenAiGeradorRespostaAdapter`: saída estruturada nativa, temperatura 0, 600 tokens, sem ferramentas, 1 nova tentativa para timeout/429/5xx/rede, sem repetição para 400/401/403, tudo traduzido para `ModeloIndisponivelException`; JSON fora do schema vira `SaidaModeloInvalidaException` |
| T057 | `ValidadorRespostaModelo`: resposta vazia, rótulo `T<n>` no texto, `conhecimentoSuficiente` sem referência, referência inexistente e preço, desconto, parcela, data, horário e vagas sem evidência literal nos trechos citados |
| T058 | `DerivadorFontes`: fontes copiadas do MCP, sem repetição, na ordem do contexto |
| T059 | `ResponderPerguntaService` completo: até 2 novas tentativas com o motivo da reprovação, `conhecimentoSuficiente=false` ou 3 reprovações → mensagem configurada, prazo único |
| T060 | `PromptInjectionIT` (9) e `ServicosIntegradosIT` com a resposta de 200 e a fonte vinda do MCP real, contra o JAR da api-conhecimento |
| T061 | Propriedades `spring.ai.chat.observations.*` e `spring.ai.chat.client.observations.*` conferidas no jar e fixadas em `false`; `LogsSemDadosSensiveisIT` (6) |

#### Arquivos criados (em `api-atendimentos/src`)

- `main`: `dominio/{ContextoRag, ReferenciaTrecho}`, `dominio/excecao/SaidaModeloInvalidaException`, `aplicacao/{MontadorContextoRag, ValidadorRespostaModelo, DerivadorFontes}`, `infraestrutura/ia/{RespostaModelo, ChatClientConfig, OpenAiGeradorRespostaAdapter}`, `resources/prompts/responder-pergunta.{system,user}.v1.st`
- `test`: `aplicacao/{MontadorContextoRagTest, ValidadorRespostaModeloTest, DerivadorFontesTest}`, `infraestrutura/ia/OpenAiGeradorRespostaAdapterIT`, `integracao/{PromptInjectionIT, LogsSemDadosSensiveisIT}`, `suporte/TesteComOpenAiFalsa`
- Removido: `infraestrutura/ia/GeradorRespostaNaoImplementado`

#### Arquivos modificados

- `dominio/RespostaGerada` (agora a saída crua do modelo: `resposta`, `conhecimentoSuficiente`, `referencias`), `aplicacao/ResponderPerguntaService`, `aplicacao/porta/GeradorRespostaPort` (recebe `ContextoRag`, motivo da reprovação e `Prazo`), `infraestrutura/config/{AtendimentoConfig, PropriedadesAtendimentos}`
- `application.yml`: `contexto`, `validacao`, `prompt`, `resiliencia.chat-*`, observações do Spring AI em `false` e `logging.level.io.modelcontextprotocol: INFO`
- Testes: `ResponderPerguntaServiceTest` (reescrito, 22), `ServicosIntegradosIT` (agora espera 200 com a fonte), `suporte/OpenAiFalsa` (endpoint de chat roteirizado), `LimiteTamanhoCorpoFilterTest`
- `README.md`, `specs/{04, 05, 08}`

#### Comandos executados e resultados

| Comando | Resultado |
|---|---|
| `./mvnw -B -pl api-atendimentos test -Dtest='ResponderPerguntaServiceTest,OpenAiGeradorRespostaAdapterIT'` | 1ª execução: falhou a compilação dos testes antigos; depois, 1 falha por partida a frio (ver "Decisões"); final: 44 de 44 |
| `... -Dtest='PromptInjectionIT'` | 1ª execução **FALHOU** (503 no primeiro teste, partida a frio); depois do aquecimento, 9 de 9 |
| `... -Dtest='LogsSemDadosSensiveisIT'` | 1ª execução **FALHOU** (5 de 5): o SDK do MCP loga a pergunta e os trechos em `DEBUG`. Corrigido (ver "Decisões"); final 6 de 6 |
| `./mvnw -B clean verify` (raiz), final | **BUILD SUCCESS**: `api-conhecimento` **539 testes**, 0 falhas, 1 ignorado; `api-atendimentos` **351 testes** (eram 256), 0 falhas, 1 ignorado |

#### Decisões e desvios

- **Degrau da solução:** RAG com validação em código e uma chamada ao modelo (sem agente, sem ferramentas). Descartei dar a ferramenta MCP ao modelo (agente): aumentaria custo e superfície de ataque sem ganho, já que a busca é determinística.
- **Fontes saem do MCP, nunca do modelo.** O modelo só devolve rótulos `T<n>`; descartei pedir título/fonte ao modelo porque ele poderia inventá-los.
- **Validação de valores é conservadora.** Reprova padrão de valor (R$, %, `6x`, datas, horários, vagas) sem evidência literal normalizada (sem espaço, caixa e acento). Números comuns ("2 anos", "40 horas") não reprovam. Um `2x` ou `3h` sem relação com preço também reprova (falso positivo aceito: o aluno recebe a mensagem de recusa em vez de um valor possivelmente inventado).
- **Campo ausente na saída do modelo é saída inválida** (`required=true`), não valor padrão.
- **Lacuna fechada na T060:** a pergunta do aluno entrava no prompt sem neutralizar delimitadores (dava para forjar `</pergunta>`). O serviço passa a sanitizar o texto enviado ao modelo; a busca MCP continua com a pergunta mascarada.
- **`RespostaGerada` aceita referência nula** para o validador reprovar em vez de uma `NullPointerException`.
- **Achado de log (T061):** com `io.modelcontextprotocol` em `DEBUG` o SDK escreve o JSON-RPC inteiro (pergunta mascarada e texto dos trechos). O logger ficou fixo em `INFO` no `application.yml`; quem o subir de propósito reabre o vazamento. Com `DEBUG` em `org.springframework.ai`, `com.openai` e `br.com.coti` nada sensível aparece.
- **Sem WireMock:** a `OpenAiFalsa` (servidor do JDK) ganhou o chat roteirizado. Nenhuma dependência nova.
- **Partida a frio nos testes:** a primeira chamada da JVM passa de 1,5 s; `TesteComOpenAiFalsa` aquece uma vez. Em produção o limite é 20 s.
- **`ServicosIntegradosIT` apontava o chat para a OpenAI real** (com chave falsa, e passava por causa do 401). Agora usa a OpenAI falsa. Os demais testes que chegam ao gerador usam `TesteComOpenAiFalsa` ou dublê do serviço.

#### Pendências e riscos

- **Nenhuma prova com o modelo real.** Nem formato (`json_schema` estrito aceito pelo `OPENAI_CHAT_MODEL` que você configurou), nem qualidade das respostas, nem eval. Nome e comportamento do modelo `gpt-6-luna` que apareceu no seu `.env` **não conferi**. Precisa da sua autorização para um teste com a chave real (custo; pergunta e trechos de teste, sem dado pessoal) e dos documentos reais (T067) para o eval da Fase 8.
- **Mensagens do `.env`.** `ATENDIMENTO_MENSAGEM_SEM_EVIDENCIA` e `ATENDIMENTO_CANAL_HUMANO` estão como hexadecimais aleatórios (pelos comandos que você rodou); troque por texto de verdade, por exemplo "Não encontrei essa informação nos materiais oficiais da COTI." e "Fale com a nossa equipe: https://www.coti.com.br/contato". Não li nem editei o `.env`.
- **LGPD, transferência internacional (art. 33):** a pergunta mascarada e os trechos agora vão à OpenAI. Mascarar CPF, e-mail e telefone não cobre nome próprio nem dado livre digitado. Confirmar contrato de operador, sem treino com o dado e com retenção definida, antes de produção. Não sei se isso existe no projeto.
- **Falso positivo do validador** em `Nx`/`Nh` sem relação com preço pode recusar respostas legítimas; medir no eval.
- **Sem validação no Docker nesta fase:** o chat só tem OpenAI simulada nos testes automatizados; subir a stack com a chave falsa não exercita a geração.
- **Janela de contexto e custo:** 8.000 caracteres de trechos + prompt + 600 tokens de saída por tentativa, até 3 tentativas por pergunta; o teto diário de custo continua para a Fase 8/produção.
- Itens antigos: allowlists de e-mail e telefone institucionais vazias, scanner de segredos (T066), documentos reais (T067).

#### Próxima fase

**Fase 8 — Testes, segurança e observabilidade** (T062–T075). T062 (limite de requisições) já foi feita antes da Fase 7. A próxima é a **T063** (acesso restrito a métricas). T066 depende de você aprovar a ferramenta do verificador de segredos (proposta: gitleaks, MIT) e T067 dos documentos reais.

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
