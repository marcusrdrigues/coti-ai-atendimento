# Tarefas do MVP — COTI AI Atendimento

Tarefas atômicas derivadas de [`04-plan.md`](04-plan.md). Cada tarefa:

- é pequena (cabe em uma sessão de trabalho) e tem uma verificação objetiva;
- lista as tarefas de que depende (**Dep.**); só começa quando elas estão marcadas;
- entrega seus próprios testes (AGENTS.md, seção 5) e termina com `./mvnw -pl <serviço> verify` passando, quando há código;
- segue o fluxo Spec → Plan → Implement → Test → Review e o relatório do AGENTS.md.

Legenda: `CON` = `api-conhecimento`, `ATE` = `api-atendimentos`, `INF` = infraestrutura/repositório. Tarefas marcadas **(spike)** só investigam e registram o resultado em `04-plan.md`; não produzem código de produção.

---

## Fase 1 — Infraestrutura Docker e PostgreSQL/pgvector

- [ ] **T001** `INF` — Revisar `.gitignore` (incluir `.env`, `target/`, `.idea/`, `*.iml`, `.vscode/`) e criar `.env.example` com todas as variáveis de `03-architecture.md` §5.1, sem valores.
  Dep.: —. Verificação: `git check-ignore .env` retorna `.env`; `.env.example` sem nenhum valor preenchido.
- [ ] **T002** `INF` **(spike)** — Conferir no Docker Hub a tag `pgvector/pgvector:0.8.2-pg18-trixie` (ou a tag pg18 com pgvector 0.8.x vigente) e registrar em `04-plan.md` §3.
  Dep.: —. Verificação: tag registrada com link da fonte.
- [ ] **T003** `INF` — Criar `docker/postgres/init/01-roles.sh`: banco `coti_conhecimento`, papéis `coti_owner` (dono) e `coti_app` (DML sem DDL), senhas lidas do ambiente.
  Dep.: T001. Verificação: revisão do script (sem senha literal).
- [ ] **T004** `INF` — Criar `docker-compose.yml` com o serviço `postgres` (imagem de T002, `127.0.0.1:5434:5432`, volume nomeado, `healthcheck` com `pg_isready`, init de T003).
  Dep.: T002, T003. Verificação: `docker compose up -d postgres` e `docker compose ps` mostram `healthy`.
- [ ] **T005** `INF` — Validar papéis e extensão: `coti_app` conecta e não cria tabela; extensão `vector` 0.8.2 disponível.
  Dep.: T004. Verificação: comandos `psql` da Fase 1 do plano com a saída no relatório.
- [ ] **T006** `INF` **(spike)** — Conferir e registrar em `04-plan.md` §3: patch do Spring Boot 4.1.x compatível com Spring AI 2.0.1; versões de springdoc 3.x, ArchUnit e WireMock; pacotes do Testcontainers na linha usada pelo Boot; nome do starter do MCP Client com Streamable HTTP.
  Dep.: —. Verificação: tabela §3 preenchida, cada linha com fonte oficial.
- [ ] **T007** `INF` — Seção "Ambiente local" no `README.md`: pré-requisitos (Docker Desktop, JDK 21), `.env` a partir do `.env.example`, subir o banco, recriar com `docker compose down -v`.
  Dep.: T005. Verificação: seguir o README do zero sobe o banco.

## Fase 2 — Estrutura Maven e inicialização dos dois microsserviços

- [ ] **T008** `INF` — Criar `pom.xml` agregador (`packaging pom`, módulos `api-conhecimento` e `api-atendimentos`) e Maven Wrapper.
  Dep.: T006. Verificação: `./mvnw -v` mostra Maven 3.9.x e JDK 21.
- [ ] **T009** `CON` — Criar `api-conhecimento/pom.xml` (parent `spring-boot-starter-parent` da versão de T006, `spring-ai-bom:2.0.1`, starters web, validation, security, actuator, jdbc), `ApiConhecimentoApplication`, `application.yml` (porta 8081, virtual threads, ECS) e `application-local.yml`.
  Dep.: T008. Verificação: `./mvnw -pl api-conhecimento verify` passa (teste de contexto com propriedades de teste).
- [ ] **T010** `ATE` — Criar `api-atendimentos/pom.xml` (mesma base, sem JDBC), `ApiAtendimentosApplication`, `application.yml` (porta 8082, `spring.ai.model.embedding: none`) e `application-local.yml`.
  Dep.: T008. Verificação: `./mvnw -pl api-atendimentos verify` passa.
- [ ] **T011** `CON` `ATE` — Propriedades tipadas (`record` + `@Validated`) dos prefixos `coti.*` e checagem das variáveis obrigatórias; teste de subida sem variável obrigatória.
  Dep.: T009, T010. Verificação: `InicializacaoSemVariavelTest` prova falha com mensagem que cita o nome da variável e não o valor.
- [ ] **T012** `CON` `ATE` — `CorrelacaoFilter`: `X-Correlation-Id` = trace id em toda resposta e no MDC.
  Dep.: T009, T010. Verificação: `CorrelacaoFilterTest` nos dois serviços.
- [ ] **T013** `CON` `ATE` — `ManipuladorErrosRest`, `CodigoErro` (catálogo do contrato §2.2) e `ProblemaFactory`; `include-stacktrace=never`; Jackson falhando em propriedade desconhecida; limite de corpo de 256 KB.
  Dep.: T012. Verificação: testes de 404, 405, 413, 415 e 500 genérico em `application/problem+json` com `codigo`, `correlacaoId`, `timestamp`, sem stack trace.
- [ ] **T014** `CON` `ATE` — `SecurityConfig` (stateless, CSRF off, CORS sem origens, nega por padrão), `ApiKeyAuthFilter` e `ComparadorChave` (tempo constante); públicos: `/actuator/health/**`, `/actuator/info` e, no ATE, `POST /api/atendimentos/perguntar`.
  Dep.: T013. Verificação: `SecurityConfigTest` (401 em rota protegida sem chave; rotas públicas acessíveis).
- [ ] **T015** `CON` `ATE` — Actuator: expor só `health`, `info`, `metrics`, `prometheus`; `metrics`/`prometheus` e detalhes do health exigem `MONITORAMENTO_API_KEY` em `X-Api-Key`; `liveness`/`readiness` habilitados.
  Dep.: T014. Verificação: `ActuatorAcessoTest` (público só `status`; `env`/`heapdump` 404; métricas 401 sem chave).
- [ ] **T016** `CON` `ATE` — springdoc 3.x: `/v3/api-docs` e Swagger UI só no perfil `local`; `OpenApiConfig` com os esquemas de segurança do contrato.
  Dep.: T014. Verificação: teste obtém `/v3/api-docs`; perfil padrão sem `/swagger-ui.html`.
- [ ] **T017** `CON` `ATE` — `ArquiteturaTest` (ArchUnit): direção `api → aplicacao → dominio`, `infraestrutura → aplicacao/dominio`; domínio sem Spring, Spring AI, JDBC, MCP, Jackson; ATE sem JDBC, `VectorStore`, `EmbeddingModel`.
  Dep.: T009, T010. Verificação: teste passa e falha ao introduzir de propósito uma dependência proibida (verificado localmente, não versionado).
- [ ] **T018** `CON` `ATE` — `Dockerfile` multi-stage com usuário não root e tag de base fixa; acrescentar as duas APIs ao `docker-compose.yml` (portas `127.0.0.1:8081` e `127.0.0.1:8082`; ATE sem `depends_on` de saúde da CON).
  Dep.: T004, T015. Verificação: `docker compose up -d --build`; `curl` em `/actuator/health` das duas APIs responde `UP`.

## Fase 3 — Persistência e ingestão de conhecimento

- [ ] **T019** `CON` — Dependências Flyway e PostgreSQL; migrações `V1__extensoes.sql` (`vector`, `hstore`, `uuid-ossp`), `V2__documento.sql` (tabela de `03-architecture.md` §4.1, `hash_conteudo` único, `CHECK` de status e categoria), `V3__vector_store.sql` (`vector(1536)`, índice HNSW `vector_cosine_ops`); Flyway com `coti_owner`, aplicação com `coti_app`.
  Dep.: T005, T009. Verificação: migração aplicada no banco do compose.
- [ ] **T020** `CON` — `PostgresContainerConfig` (Testcontainers, imagem de T002, `@ServiceConnection`, papéis criados como no compose) e `MigracoesIT`.
  Dep.: T019. Verificação: `MigracoesIT` prova schema criado e que `coti_app` não executa `CREATE TABLE`.
- [ ] **T021** `CON` — Domínio: `Documento`, `StatusDocumento`, `Categoria`, `Pendencia` e exceções `ConhecimentoDuplicadoException`, `DadoPessoalDetectadoException`, `ConhecimentoNaoEncontradoException`.
  Dep.: T017. Verificação: testes unitários das invariantes (campos obrigatórios, transições de status permitidas).
- [ ] **T022** `CON` — `NormalizadorTexto` e `HashConteudo` (SHA-256 do texto normalizado).
  Dep.: T021. Verificação: `NormalizadorTextoTest` (controle, quebras, espaços; textos equivalentes → mesmo hash).
- [ ] **T023** `CON` — `AnalisadorConteudoPort` e `AnalisadorConteudoRegex` com padrões configuráveis: CPF com dígito verificador, e-mail, telefone; padrões de injeção e delimitadores do prompt.
  Dep.: T021. Verificação: `AnalisadorConteudoRegexTest` com positivos e negativos (CPF inválido não dispara).
- [ ] **T024** `CON` — `RepositorioDocumentoPort` e `DocumentoJdbcRepositorio` (`JdbcClient`): salvar, buscar por hash, buscar por id, listar com filtros e paginação, atualizar status.
  Dep.: T020, T021. Verificação: `DocumentoJdbcRepositorioIT`.
- [ ] **T025** `CON` — `CadastrarConhecimentoService` (caminhos de rejeição): 409 duplicado, 422 dado pessoal sem gravar, `EM_REVISAO` com pendência; caminho feliz grava `PROCESSANDO` e delega a uma porta de indexação ainda sem implementação (concluída em T035).
  Dep.: T022, T023, T024. Verificação: `CadastrarConhecimentoServiceTest` com dublês.
- [ ] **T026** `CON` — `ConhecimentoController` `POST /api/conhecimentos` + DTOs `record` com Bean Validation; chave administrativa obrigatória (chave MCP recusada).
  Dep.: T014, T025. Verificação: `ConhecimentoControllerTest`: 400 por regra e campo extra sem eco do valor, 401, 409, 413, 415, 422, 202 com `pendencias`.
- [ ] **T027** `CON` — `ConsultarConhecimentosService` + `GET /api/conhecimentos` e `GET /api/conhecimentos/{id}` (RF11).
  Dep.: T024, T026. Verificação: `ConsultarConhecimentosIT`: filtros, paginação, `tamanho=101` → 400, listagem sem `conteudo`, detalhe com `pendencias`, 404 e 400 para id inválido.

## Fase 4 — Chunking e geração de embeddings

- [ ] **T028** `CON` — `DivisorTexto` e `ConfiguracaoDivisao` (1.500/200/50/500) conforme skill `rag-pgvector` §4.
  Dep.: T021. Verificação: `DivisorTextoTest`: limite de tamanho, sobreposição, corte em frase, texto curto, determinismo, mais de 500 trechos, configuração inválida, texto sem espaços sem laço.
- [ ] **T029** `CON` — `IdTrecho` (UUID v5 de `documentoId:ordem`) e `VigenciaMetadado` (`AAAAMMDD`, `99991231`).
  Dep.: T021. Verificação: testes unitários de determinismo e conversão.
- [ ] **T030** `CON` — `EmbeddingModelFalso` determinístico (vetores de 1536 por texto, com modos lento e com falha configuráveis) em `@TestConfiguration`.
  Dep.: T020. Verificação: usado por um teste de contexto sem `OPENAI_API_KEY` real.
- [ ] **T031** `CON` **(spike)** — Confirmar no Spring AI 2.0.1: tipo da coluna `metadata`, assinatura de `vectorStore.delete(Filter.Expression)`, comportamento de `add()` com id já existente, recursos de retry do Spring Framework 7. Registrar em `04-plan.md`.
  Dep.: T019. Verificação: registro com fonte e, quando possível, teste mínimo.
- [ ] **T032** `CON` — Configuração do `EmbeddingModel` (OpenAI, `text-embedding-3-small`, 1536, `max-retries: 0`) e do `PgVectorStore` (`initialize-schema=false`, `schema-validation=true`, HNSW, cosseno).
  Dep.: T019, T030. Verificação: contexto sobe com o dublê; propriedades em `application.yml` sem literal de chave.
- [ ] **T033** `CON` — `RepositorioVetorialPort` e `PgVectorRepositorioAdapter`: gravar lote com metadados (status `PROCESSANDO`, `vigenteAte` inteiro), remover por `documentoId`.
  Dep.: T029, T031, T032. Verificação: `PgVectorRepositorioAdapterIT` confere metadados e remoção.
- [ ] **T034** `CON` — `PublicacaoPort` e `PublicacaoJdbcAdapter`: transação curta que muda chunks e documento para `PUBLICADO`.
  Dep.: T033. Verificação: `PublicacaoAtomicaIT` (falha simulada no meio → nada muda).
- [ ] **T035** `CON` — `PoliticaNovasTentativas` (backoff exponencial com variação, só transitórios) e caminho feliz do `CadastrarConhecimentoService`: divisão → lotes de 100 → publicação → 201; compensação em falha → `ERRO_PROCESSAMENTO` e 503 `MODELO_INDISPONIVEL` com `conhecimentoId`; 413 acima de 500 trechos.
  Dep.: T025, T028, T034. Verificação: `PoliticaNovasTentativasTest` e `CadastroCompletoIT` (sucesso, 429 seguido de sucesso, falha no segundo lote sem chunks restantes, 401 sem nova tentativa).
- [ ] **T036** `CON` — `LimpezaDocumentosInterrompidos` (`ApplicationRunner`): documentos `PROCESSANDO` há mais de 10 minutos e seus chunks.
  Dep.: T035. Verificação: `LimpezaDocumentosInterrompidosIT`.

## Fase 5 — Busca vetorial e MCP Server

- [ ] **T037** `CON` **(spike)** — `ScoreCossenoIT`: confirmar que `Document.getScore()` com `COSINE_DISTANCE` é similaridade de 0 a 1 e que `gte` sobre inteiro funciona no filtro JSONPath.
  Dep.: T035. Verificação: teste versionado e resultado registrado em `04-plan.md`.
- [ ] **T038** `CON` — `BuscarConhecimentoService`: validação (`pergunta` 3–1.000, `limite` 1–10, padrão 5), timeout de 5 s no embedding da pergunta, filtro (`PUBLICADO`, modelo, vigência), limiar 0,70, ordenação, cortes 2.000/8.000.
  Dep.: T037. Verificação: `BuscarConhecimentoServiceTest` e `BuscaVetorialIT` (`EM_REVISAO`, `PROCESSANDO`, vencido e outro modelo nunca aparecem; lista vazia é sucesso).
- [ ] **T039** `CON` — `DiagnosticoBuscaController` (RF12) com DTOs `record`, chave administrativa e campos de diagnóstico.
  Dep.: T026, T038. Verificação: `DiagnosticoBuscaControllerTest` (400, 401, 200 com `limiarSimilaridade`, `modeloEmbedding`, `duracaoMs`).
- [ ] **T040** `CON` — MCP Server: starter webmvc, `protocol: STREAMABLE`, `BuscarConhecimentoTool` (`@McpTool` com anotações de somente leitura, `generateOutputSchema`), `ResultadoBusca`, `TrechoResultado`, erros como `RuntimeException` com `ErroFerramenta`.
  Dep.: T038. Verificação: teste unitário da ferramenta com o serviço falso.
- [ ] **T041** `CON` — Cadeia de segurança de `/mcp/**` com `Bearer CONHECIMENTO_MCP_API_KEY`; chave administrativa recusada.
  Dep.: T014, T040. Verificação: `SegurancaMcpIT` (sem chave, chave errada e chave administrativa → 401).
- [ ] **T042** `CON` — `BuscarConhecimentoToolContratoIT` com cliente MCP real em porta aleatória: `tools/list` (schemas e anotações do contrato §4.2), `tools/call` válido, `limite` padrão, argumentos inválidos (`ARGUMENTOS_INVALIDOS`), banco indisponível (`BASE_CONHECIMENTO_INDISPONIVEL`), formato do texto de erro.
  Dep.: T041. Verificação: teste passa; divergências de schema geradas pelo `@McpTool` registradas.
- [ ] **T043** `CON` — `DiagnosticoIgualMcpIT`: mesma pergunta pelo diagnóstico e pelo MCP devolve os mesmos trechos e similaridades.
  Dep.: T039, T042. Verificação: teste passa.

## Fase 6 — MCP Client e API de Atendimentos

- [ ] **T044** `ATE` **(spike)** — Confirmar no Spring AI 2.0.1 / MCP Java SDK: artefato do MCP Client, injeção do `McpSyncClient` da conexão, construção do `CallToolRequest`, customização de cabeçalhos via `McpClientCustomizer`, efeito de `initialized: true` sem servidor, propriedades de status customizado do Actuator.
  Dep.: T006, T010. Verificação: resultado registrado em `04-plan.md`.
- [ ] **T045** `ATE` — Domínio: `PerguntaAluno`, `TrechoConhecimento`, `Fonte`, `ResultadoAtendimento`, `Prazo` e exceções `BaseConhecimentoIndisponivelException`, `TempoEsgotadoException`.
  Dep.: T017. Verificação: `PrazoTest` (tempo restante, expiração com `Clock` injetado).
- [ ] **T046** `ATE` — `MascaradorPort` e `MascaradorDadosPessoaisRegex` (CPF, e-mail, telefone).
  Dep.: T045. Verificação: `MascaradorDadosPessoaisRegexTest`.
- [ ] **T047** `ATE` — DTOs `PerguntarRequest`, `RespostaAtendimentoResponse`, `FonteResponse` e `AtendimentoController` com Bean Validation; caso de uso substituído por dublê.
  Dep.: T013, T045. Verificação: `AtendimentoControllerTest` (400 para 2 e 1.001 caracteres e campo extra, sem chamar o caso de uso; 200 no formato do contrato).
- [ ] **T048** `ATE` — `ServidorMcpDeTeste`: servidor MCP mínimo em teste que publica `buscar_conhecimento` com o contrato §4 e respostas configuráveis (sucesso, vazio, `isError`, fora do schema, lento, 401).
  Dep.: T044. Verificação: um teste usa o servidor e lista a ferramenta.
- [ ] **T049** `ATE` — Configuração do MCP Client (`SYNC`, `request-timeout` 5 s, `toolcallback.enabled=false`, conexão `conhecimento`) e `McpCabecalhosCustomizer` (Bearer + `traceparent`).
  Dep.: T044. Verificação: teste com `ServidorMcpDeTeste` confere os dois cabeçalhos recebidos.
- [ ] **T050** `ATE` — `ValidadorResultadoBusca` e `BaseConhecimentoMcpAdapter`: `tools/call`, `isError`, validação do `outputSchema`, conversão para domínio, até 2 novas tentativas só em timeout/conexão, respeitando o `Prazo`.
  Dep.: T046, T048, T049. Verificação: `ValidadorResultadoBuscaTest` e `BaseConhecimentoMcpAdapterIT` (sucesso, `isError`, fora do schema, `id` repetido, lento, 401).
- [ ] **T051** `ATE` — `GeradorRespostaPort` (só a interface) e `ResponderPerguntaService` sem modelo: mascarar → buscar → vazio = sem evidência com mensagem configurada; falhas MCP → 503 `BASE_CONHECIMENTO_INDISPONIVEL`; prazo → 503 `TEMPO_ESGOTADO`.
  Dep.: T047, T050. Verificação: `ResponderPerguntaServiceTest` (gerador nunca chamado com lista vazia; texto mascarado é o enviado).
- [ ] **T052** `ATE` — `ConhecimentoMcpHealthIndicator` (ping ou `tools/list`, 2 s) e status agregado `DEGRADADO` (HTTP 200); `liveness`/`readiness` sem o indicador.
  Dep.: T015, T044, T049. Verificação: `HealthDegradadoIT` e `SubidaSemConhecimentoIT` (serviço sobe e responde 503 do contrato com a CON fora).
- [ ] **T053** `ATE` `CON` — Teste de integração entre serviços reais: ATE contra a CON real (Testcontainers) para a pergunta sem evidência e para 401 com chave MCP errada.
  Dep.: T043, T051. Verificação: teste passa nos dois sentidos.

## Fase 7 — Integração com OpenAI e montagem do contexto RAG

- [ ] **T054** `ATE` — `ContextoRag`, `ReferenciaTrecho` e `MontadorContextoRag` (contrato §6.3).
  Dep.: T045. Verificação: `MontadorContextoRagTest`: limiar, `similaridade` nula no fim, neutralização de delimitadores, escape de aspas, 2.000/8.000/5 com remoção dos de menor similaridade, rótulos `T1..Tn`.
- [ ] **T055** `ATE` — Prompts `responder-pergunta.system.v1.st` e `responder-pergunta.user.v1.st` com delimitadores e regras do contrato §6.3–6.4.
  Dep.: T054. Verificação: teste carrega os recursos e confirma um único bloco `<trechos>` e um `<pergunta>` no prompt renderizado.
- [ ] **T056** `ATE` — `RespostaModelo` (`record`), `ChatClientConfig` (sem ferramentas) e `OpenAiGeradorRespostaAdapter` (saída estruturada nativa, temperatura 0, 600 tokens, modelo de `OPENAI_CHAT_MODEL`, 1 nova tentativa em transitório, tradução para `ModeloIndisponivelException`).
  Dep.: T051, T055. Verificação: `OpenAiGeradorRespostaAdapterIT` com WireMock (200 válido, 429, 500, timeout, 401, JSON inválido) e conferência da requisição enviada (sem `tools`).
- [ ] **T057** `ATE` — `ValidadorRespostaModelo`: referência inexistente, `conhecimentoSuficiente` sem referência, valores monetários, datas, horários, vagas e condições comerciais sem evidência literal.
  Dep.: T054. Verificação: `ValidadorRespostaModeloTest` com positivos e falsos positivos conhecidos ("2 anos de experiência" não reprova).
- [ ] **T058** `ATE` — `DerivadorFontes`: cópia dos campos do trecho MCP pelas referências, sem repetição, ordenada; rótulos fora da resposta.
  Dep.: T054. Verificação: `DerivadorFontesTest`.
- [ ] **T059** `ATE` — `ResponderPerguntaService` completo: geração, até 2 novas tentativas por saída reprovada com o motivo, `conhecimentoSuficiente=false` → mensagem configurada, fontes, prazo.
  Dep.: T056, T057, T058. Verificação: `ResponderPerguntaServiceTest` completo.
- [ ] **T060** `ATE` — `AtendimentoFimAFimIT` e `PromptInjectionIT` com `ServidorMcpDeTeste` + WireMock: resposta com fontes iguais ao MCP, referência inventada, preço inventado, delimitador forjado no trecho, resposta sem prompt/modelo/tokens.
  Dep.: T059. Verificação: testes passam.
- [ ] **T061** `ATE` **(spike)** — Confirmar e configurar a propriedade que mantém o conteúdo de prompt e completion fora das observações do Spring AI.
  Dep.: T056. Verificação: teste de captura de log com o fluxo completo sem a pergunta.

## Fase 8 — Testes, segurança e observabilidade

- [ ] **T062** `CON` `ATE` — `LimiteRequisicoesFilter` e `BaldeTokens`: `perguntar` 10/min por IP; cadastro 30/min e diagnóstico 10/min por credencial; 429 com `Retry-After`.
  Dep.: T039, T047. Verificação: `LimiteRequisicoesFilterTest` e `LimiteRequisicoesIT`.
- [ ] **T063** `CON` `ATE` — Métricas `coti.atendimento.resultado`, `coti.atendimento.etapa.duracao`, `coti.atendimento.saida_reprovada`, `coti.conhecimento.cadastro`, com tags de versão de prompt e modelos.
  Dep.: T036, T059. Verificação: `MetricasAtendimentoIT` lê as métricas via Actuator com a chave de monitoramento.
- [ ] **T064** `CON` `ATE` — `LogsSemDadoSensivelIT` (captura de saída): pergunta, CPF, texto de trecho e chaves ausentes dos logs nos fluxos A e B.
  Dep.: T036, T061. Verificação: teste passa.
- [ ] **T065** `ATE` `CON` — `PropagacaoTraceIT`: o mesmo trace id nos logs da ATE e da CON para uma pergunta.
  Dep.: T053. Verificação: teste passa.
- [ ] **T066** `INF` — Propor verificador de segredos (ferramenta, licença, manutenção) e, após aprovação, configurar no repositório.
  Dep.: —. Verificação: aprovação registrada; verificação sem achados.
- [ ] **T067** `INF` — Obter da COTI os documentos oficiais públicos para a base inicial (cursos, programas, modalidades, pré-requisitos, institucional).
  Dep.: —. Verificação: documentos recebidos e revisados quanto a dados pessoais.
- [ ] **T068** `CON` — Eval de recuperação: `eval/recuperacao.jsonl` (~20 casos) e `ExecutorEvalRecuperacao` (`@Tag("eval")`, perfil `eval`).
  Dep.: T043, T067. Verificação: `./mvnw -pl api-conhecimento verify -Peval` gera relatório com acerto no top-k.
- [ ] **T069** `ATE` — Eval de resposta e adversarial: `eval/resposta.jsonl` (~20) e `eval/adversarial.jsonl` (10+), `ExecutorEvalResposta` com verificação por código.
  Dep.: T060, T067. Verificação: `./mvnw -pl api-atendimentos verify -Peval` gera relatório por categoria.
- [ ] **T070** `ATE` — Escolher `OPENAI_CHAT_MODEL` (identificador com versão) pelo eval: menor modelo que atinge as notas mínimas; registrar em `03-architecture.md`.
  Dep.: T069. Verificação: relatório comparativo anexado; `.env.example` documenta o valor escolhido sem a chave.
- [ ] **T071** `INF` — Escrever `specs/07-test-plan.md`: matriz critério de aceite (RF01–RF12, RNF01–RNF09) → teste/eval, notas mínimas, como rodar.
  Dep.: T060, T068, T069. Verificação: todo critério de aceite tem linha na matriz.
- [ ] **T072** `INF` — Escrever `specs/08-checklist.md`: itens de `03-architecture.md` §11.2, LGPD (contrato com a OpenAI) e definição de pronto.
  Dep.: T071. Verificação: revisão do checklist com você.
- [ ] **T073** `INF` — Revisão OWASP Top 10 para LLM: tabela item, situação e evidência (arquivo, teste, caso de eval).
  Dep.: T071. Verificação: nenhum "atende" sem evidência; "não atende" com plano.
- [ ] **T074** `INF` — `scripts/smoke-local.sh`: com o compose no ar, cadastra um documento público e faz uma pergunta, mostrando resposta com fonte.
  Dep.: T018, T060. Verificação: script executado com a saída no relatório.
- [ ] **T075** `INF` — Atualizar `README.md`: arquitetura resumida, como rodar, testar, rodar eval e smoke.
  Dep.: T074. Verificação: seguir o README do zero reproduz o smoke.

---

## Primeira implementação

Tarefas que podem ser executadas já, sem violar dependências:

| Tarefa | Por que pode começar |
|---|---|
| **T001** | Sem dependência |
| **T002** (spike) | Sem dependência |
| **T006** (spike) | Sem dependência; desbloqueia toda a Fase 2 |
| **T066** | Sem dependência; só a proposta, a configuração espera sua aprovação |
| **T067** | Sem dependência; depende da COTI e convém pedir cedo, pois bloqueia o eval |

Na sequência imediata, conforme as anteriores forem concluídas: **T003 → T004 → T005 → T007** (fecham a Fase 1) e **T008 → T009/T010** (abrem a Fase 2, assim que T006 estiver registrada).

Sugestão de primeiro lote de trabalho: **T001, T002, T003, T004, T005, T006 e T007** — fecha a Fase 1 e deixa as versões confirmadas para iniciar a Fase 2.
