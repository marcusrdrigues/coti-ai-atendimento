---
name: mcp-integration
description: Orienta a integração por MCP entre a api-conhecimento (MCP Server) e a api-atendimentos (MCP Client) com Spring AI 2.0.1 — transporte Streamable HTTP, publicação, descoberta e execução da ferramenta de pesquisa, validação de parâmetros, autenticação, timeouts e erros. Use ao criar ou alterar qualquer lado da comunicação MCP.
---

# Skill: Integração MCP (Server e Client)

Referência: Spring AI 2.0.1. Starters, propriedades e anotações citados foram conferidos na documentação de referência do Spring AI 2.0. Itens marcados como **(conferir)** não foram confirmados e precisam ser checados na documentação da versão fixada antes do uso.

## Quando usar

- Publicar ou alterar a ferramenta de pesquisa na `api-conhecimento`.
- Configurar o cliente MCP na `api-atendimentos`.
- Mudar transporte, endpoint, autenticação, timeout ou tratamento de erros entre os serviços.
- Alterar o contrato da ferramenta (nome, parâmetros, saída), sempre junto com `specs/06-api-contracts.md`.

Não use esta skill para: a lógica da busca vetorial (`rag-pgvector`) e a geração da resposta (`spring-ai`).

## Responsabilidades

| Lado | Responsabilidade |
|---|---|
| `api-conhecimento` (MCP Server) | Publicar a ferramenta de pesquisa somente leitura; validar parâmetros; aplicar o filtro de publicação; devolver trechos com fonte e similaridade; autenticar o chamador. |
| `api-atendimentos` (MCP Client) | Descobrir a ferramenta, chamá-la **pelo código** com timeout, validar a resposta, traduzir falhas em exceções de domínio. |

Esta skill garante os Artigos 4, 6, 10, 11, 13 e 17 da constituição.

## Decisões de desenho

- **Transporte: Streamable HTTP** (`spring.ai.mcp.server.protocol=STREAMABLE`). O SSE está depreciado desde a 2.0.0. O modo `STATELESS` também é Streamable HTTP, sem sessão; ele pode ser avaliado em `specs/03-architecture.md` se a ferramenta continuar sem estado e o serviço precisar escalar horizontalmente sem afinidade de sessão.
- **Chamada pelo código, não pelo modelo.** A `api-atendimentos` chama a ferramenta diretamente pelo `McpSyncClient` e passa os trechos ao `ChatClient`. A integração MCP → `ToolCallback` do Spring AI fica desligada (`spring.ai.mcp.client.toolcallback.enabled=false`). Alternativa descartada: expor a ferramenta ao modelo como tool. Isso dá ao modelo o poder de decidir pesquisar (ou não), aumenta chamadas e tira do código a decisão de "conhecimento insuficiente" (Artigos 10 e 11).
- **Cliente síncrono** (`type: SYNC`): a `api-atendimentos` é Spring MVC; virtual threads (Java 21) cobrem a espera de I/O.

## Contrato da ferramenta (resumo)

O contrato oficial está em `specs/06-api-contracts.md`, seção 4 (schemas completos, exemplos JSON-RPC e erros). Resumo; a spec prevalece.

| Item | Valor |
|---|---|
| Nome | `buscar_conhecimento` |
| Endpoint | `/mcp` (Streamable HTTP), servidor `coti-conhecimento` |
| Entrada | `pergunta` (string, obrigatório, 3 a 1.000 caracteres); `limite` (inteiro, opcional, padrão 5, 1 a 10) |
| Saída (`structuredContent`) | `trechos` (cada um com `id` do chunk, `texto` ≤ 2.000 caracteres, `documentoId`, `titulo`, `fonte`, `categoria`, `similaridade` de 0 a 1 ou `null`) e `total` |
| Limite de contexto | Soma dos `texto` ≤ 8.000 caracteres; ordenado por similaridade decrescente |
| Erros | `isError = true` com `ErroFerramenta` `{ "codigo", "mensagem" }`: `ARGUMENTOS_INVALIDOS`, `BASE_CONHECIMENTO_INDISPONIVEL`, `MODELO_INDISPONIVEL`, `ERRO_INTERNO`; HTTP 401 sem credencial |
| Anotações | `readOnlyHint=true`, `destructiveHint=false`, `idempotentHint=true`, `openWorldHint=false` |
| Credencial | `Authorization: Bearer <CONHECIMENTO_MCP_API_KEY>`; a chave administrativa é recusada |
| Nunca contém | vetor, status, modelo de embedding, metadados internos, trechos não publicados |

O endpoint REST `POST /api/conhecimentos/diagnostico/busca` executa o mesmo caso de uso, mas é **administrativo**: usa a chave administrativa e a `api-atendimentos` nunca o chama.

## Passo a passo: MCP Server (`api-conhecimento`)

### 1. Dependência e configuração

- `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` (versão pelo BOM).

```yaml
spring:
  ai:
    mcp:
      server:
        name: coti-conhecimento
        version: 1.0.0
        protocol: STREAMABLE
        # endpoint padrão: /mcp — para mudar, use spring.ai.mcp.server.streamable-http.mcp-endpoint (conferido na 2.0.1)
```

### 2. Publicar a ferramenta

- A classe da ferramenta fica na camada `api` (é um "controller MCP"): traduz o protocolo e delega a um caso de uso (`BuscarConhecimentoService`).
- Use `@McpTool` com `generateOutputSchema = true` e `@McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)`.
- Parâmetros com `@McpToolParam(description = ..., required = ...)`. A descrição é lida por clientes e modelos: seja objetiva e não inclua instruções.
- Retorne um `record` (`ResultadoBusca`) para que o schema de saída seja gerado. O teste de contrato compara o schema publicado com o da seção 4.2 do contrato.

```java
// Ilustração do formato, não é código da aplicação
@McpTool(name = "buscar_conhecimento",
         title = "Buscar conhecimento COTI",
         description = "Busca trechos publicados da base de conhecimento oficial da COTI Informática semanticamente relacionados à pergunta.",
         generateOutputSchema = true,
         annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
public ResultadoBusca buscar(
        @McpToolParam(description = "Pergunta ou texto a pesquisar na base de conhecimento.", required = true) String pergunta,
        @McpToolParam(description = "Quantidade máxima de trechos a devolver.", required = false) Integer limite) { ... }
```

- Conferido na 2.0.1: `@McpTool.McpAnnotations` aceita `openWorldHint`, mas `@McpToolParam` só tem `description` e `required`, então o `inputSchema` gerado não traz `minLength`, `maxLength`, `minimum`, `maximum`, `default` nem `additionalProperties: false` (o contrato registra a diferença na §4.2). A validação em código continua obrigatória. O SDK valida tipo e campo obrigatório antes do método.

### 3. Validar parâmetros

O schema MCP informa o cliente, mas **não** é proteção. Valide no servidor, antes de gerar o embedding:

- `pergunta`: não nula, sem espaços nas pontas, entre 3 e 1.000 caracteres.
- `limite`: nulo vira 5; fora de 1 a 10 é rejeitado.
- Rejeite com `ARGUMENTOS_INVALIDOS` (ver passo 4), sem eco do valor recebido.

### 4. Erros no servidor (comportamento da 2.0.1)

A partir da 2.0.1, o tratamento de exceções do `@McpTool` segue o mesmo padrão do `@Tool`:

| Exceção lançada pelo método | Resultado |
|---|---|
| `RuntimeException` (exceto `McpError`) | Vira `CallToolResult` com erro, devolvido ao cliente |
| Exceção checada declarada | Sobe como `UndeclaredThrowableException`: falha dura |
| `Error` ou `McpError` | Sobe sem conversão: falha dura |

Portanto:
- Erros são `RuntimeException` de domínio com um código do contrato: `ArgumentosInvalidosException` (`ARGUMENTOS_INVALIDOS`), `BaseConhecimentoIndisponivelException` (`BASE_CONHECIMENTO_INDISPONIVEL`), `ModeloIndisponivelException` (`MODELO_INDISPONIVEL`); qualquer outra vira `ERRO_INTERNO`.
- O texto de erro que chega ao cliente é o JSON `ErroFerramenta` (`{"codigo": "...", "mensagem": "..."}`), com mensagem segura, sem stack trace, SQL, tabela, classe ou valor recebido. Conferido na 2.0.1: o texto do erro é `mensagem + quebra de linha + mensagem da causa raiz`. Sem causa o JSON saía duplicado; por isso `ErroFerramentaException` leva uma causa de mensagem vazia (o texto fica sendo o JSON mais uma quebra de linha) e o cliente faz `strip()`. O teste de contrato trava esse formato.
- Não declare exceções checadas nos métodos `@McpTool`.
- O detalhe técnico da falha vai só para o log interno, com o id de correlação.

### 5. Autenticação

- O endpoint MCP exige `Authorization: Bearer <CONHECIMENTO_MCP_API_KEY>`; chamada anônima, com chave errada ou com a chave administrativa recebe 401 antes de qualquer processamento MCP.
- O mecanismo é decidido em `specs/03-architecture.md`. Opções:
  - **Chave de serviço em cabeçalho, validada por um filtro do Spring Security** (simples, sem dependência extra). A chave vem de variável de ambiente (`CONHECIMENTO_MCP_API_KEY`) e é comparada em tempo constante.
  - **Módulo de segurança MCP do Spring AI Community** (`mcpServerApiKey()` / `McpServerOAuth2Configurer`), citado na documentação de segurança MCP do Spring AI 2.0. É dependência nova: exige aprovação, com versão e licença conferidas.
- Rede: em ambiente Docker, a `api-conhecimento` não publica a porta do MCP para fora da rede interna, a menos que a spec diga o contrário.

### 6. Observabilidade no servidor

- Registre por chamada: id de correlação, nome da ferramenta, duração, quantidade de trechos, maior similaridade, código de erro. **Não** registre a pergunta completa.

## Passo a passo: MCP Client (`api-atendimentos`)

### 1. Dependência e configuração

- Starter de cliente MCP do Spring AI 2.0 com transporte Streamable HTTP (`spring-ai-starter-mcp-client`, conferido na 2.0.1). **Use `initialized: false`**: com o padrão a aplicação não sobe com o servidor MCP fora do ar; com `false` ela sobe, conecta na primeira chamada e se reabre sozinha quando o servidor volta (spike T044).

```yaml
spring:
  ai:
    mcp:
      client:
        name: coti-atendimentos
        type: SYNC
        request-timeout: ${CONHECIMENTO_MCP_TIMEOUT:5s}   # padrão do Spring AI é 20s; alto demais para atendimento
        toolcallback:
          enabled: false                                   # o modelo não recebe a ferramenta
        streamable-http:
          connections:
            conhecimento:
              url: ${CONHECIMENTO_MCP_URL}                 # ex.: http://api-conhecimento:8081
              endpoint: /mcp
```

- `initialized: true` (padrão) inicializa o cliente na criação. Teste e documente o que acontece se a `api-conhecimento` estiver fora do ar na subida; a `api-atendimentos` deve subir e responder com a mensagem de indisponibilidade, não ficar fora do ar.

### 2. Credencial e trace no cabeçalho

- Envie a credencial de serviço e o contexto de trace (`traceparent`) em toda requisição. Conferido na 2.0.1: um bean `McpSyncHttpClientRequestCustomizer` é aplicado pela autoconfiguração a toda requisição HTTP e acrescenta `Authorization` e `traceparent`. Ele roda em threads do cliente HTTP, sem o MDC; por isso um bean `McpClientCustomizer<McpClient.SyncSpec>` define `transportContextProvider(...)`, que lê o trace id do MDC na thread que chama `callTool`. Se a chamada for feita em outra thread (para limitar o tempo), copie o MDC para ela.

### 3. Adaptador de busca

- Crie `BaseConhecimentoMcpAdapter` implementando a porta `BaseConhecimentoPort` da camada de aplicação.
- O adaptador recebe o `McpSyncClient` da conexão `conhecimento` (o starter publica um bean `List<McpSyncClient>`, sem bean individual; conferido) e:
  1. chama a ferramenta `buscar_conhecimento` com `{ "pergunta": <pergunta mascarada>, "limite": 5 }` (`callTool(new CallToolRequest(nome, Map))`, conferido);
  2. se `isError` for verdadeiro, lança exceção de domínio;
  3. lê o `structuredContent`, converte para `record` e valida contra o `outputSchema` do contrato (campos obrigatórios, categoria conhecida, `similaridade` entre 0 e 1 ou `null`, `texto` ≤ 2.000, no máximo 10 trechos, sem `id` repetido). Qualquer violação rejeita o resultado inteiro;
  4. devolve objetos de domínio (`TrechoConhecimento`), nunca tipos do MCP.
- A montagem do contexto RAG (filtro, sanitização, limites, rótulos `T<n>`) **não** é responsabilidade do adaptador: fica na camada de aplicação (skill `spring-ai`, contrato seção 6.3).

### 4. Descoberta da ferramenta

- O indicador de saúde `conhecimentoMcp` faz `ping` ou `listTools` com timeout de 2 s e confirma que `buscar_conhecimento` existe com o schema esperado. Falha ou divergência deixa o status agregado `DEGRADADO` (HTTP 200) e gera log de alerta; `liveness` e `readiness` não dependem do MCP (contrato, seção 5.2).
- Não descubra ferramentas dinamicamente para expô-las ao modelo.

### 5. Timeouts e retry

- **Nunca interrompa a thread do cliente MCP** (`Future.cancel(true)`, `Thread.interrupt`): interromper a conexão inicial preguiçosa deixa o cliente travado para as chamadas seguintes. Pare de esperar e deixe o `request-timeout` do SDK terminar a chamada.
- Depois que o servidor reinicia, a primeira chamada falha com `RuntimeException("MCP session with server terminated")` (sem tipo próprio) e a seguinte reabre a sessão: repita qualquer falha de transporte (a ferramenta é de leitura e idempotente), exceto credencial recusada (`McpHttpClientTransportAuthorizationException`).

- `request-timeout` curto (padrão do projeto: 5s, ajustável).
- Retry só para timeout e erro de conexão, com backoff exponencial, variação aleatória e no máximo 2 novas tentativas. A ferramenta é somente leitura e idempotente, então repetir é seguro.
- Nenhum retry para `isError=true` de validação.

### 6. Mapeamento de falhas

Conforme o contrato, seção 6.5:

| Falha | Tentativas | Resposta ao aluno |
|---|---|---|
| Timeout (5 s) ou conexão recusada | até 2 novas | `503 BASE_CONHECIMENTO_INDISPONIVEL` |
| HTTP 401/403 | nenhuma (alerta de configuração no log) | `503 BASE_CONHECIMENTO_INDISPONIVEL` |
| `isError=true` | nenhuma | `503 BASE_CONHECIMENTO_INDISPONIVEL` |
| Resultado fora do schema | nenhuma | `503 BASE_CONHECIMENTO_INDISPONIVEL` |
| Tempo total do atendimento (30 s) estourado | interrompe | `503 TEMPO_ESGOTADO` |
| `trechos` vazio | — | Não é erro: `200` sem evidência (mensagem de recusa com canal humano) |

Todas as falhas viram `BaseConhecimentoIndisponivelException` no adaptador, com a causa registrada só no log.

## Padrões de codificação

- Tipos do MCP (`McpSchema.*`, `McpSyncClient`) só nos adaptadores e na classe da ferramenta. Domínio e aplicação usam `record`s próprios.
- Uma classe por ferramenta no servidor, delegando a um caso de uso.
- Exceções de domínio como `RuntimeException`, com mensagem segura.
- Configuração (URL, timeout, credencial) por propriedades e variáveis de ambiente.
- Injeção por construtor.

## Antipadrões proibidos

- Usar o transporte SSE (depreciado).
- A `api-atendimentos` usar o endpoint REST de diagnóstico ou qualquer outro caminho REST para buscar conhecimento.
- Aceitar a chave administrativa em `/mcp` ou a chave MCP nos endpoints REST.
- `api-atendimentos` acessar o banco, o `VectorStore` ou o `EmbeddingModel`.
- Registrar a ferramenta MCP como tool do modelo na `api-atendimentos`.
- Publicar ferramenta com efeito de escrita (ingestão, remoção) no mesmo servidor MCP consumido pelo atendimento.
- Confiar no schema MCP como validação.
- Lançar exceção checada em `@McpTool` esperando que vire mensagem de erro.
- Mensagem de erro com stack trace, SQL, nome de classe ou valor recebido.
- Endpoint MCP sem autenticação ou exposto à internet.
- Timeout padrão de 20s sem decisão explícita; retry infinito.
- Devolver vetor ou metadado interno na saída da ferramenta.
- Logar a pergunta completa.

## Critérios de validação

- Teste de integração (servidor): chamada à ferramenta via cliente MCP real contra a `api-conhecimento` com Testcontainers; confere schema de entrada e saída.
- Teste (servidor): parâmetros inválidos (`pergunta` vazia, com 2 ou 1.001 caracteres; `limite=0` e `=11`) geram `isError=true` com `codigo = ARGUMENTOS_INVALIDOS` e mensagem segura.
- Teste (servidor): sem `limite`, no máximo 5 trechos; soma dos `texto` ≤ 8.000 caracteres.
- Teste (servidor): chamada sem credencial, com credencial errada e com a chave administrativa recebe 401.
- Teste (servidor): documento não publicado nunca aparece na saída.
- Teste (cliente): servidor MCP falso com atraso maior que o timeout gera `BaseConhecimentoIndisponivelException`.
- Teste (cliente): `isError=true`, resposta fora do schema e servidor fora do ar mapeados como na tabela de falhas.
- Teste (cliente): `toolcallback` desligado; nenhum `ToolCallback` registrado no `ChatClient`.
- Teste de contrato: nome, parâmetros e campos de saída iguais a `specs/06-api-contracts.md`.

## Checklist de conclusão

- [ ] Servidor com `protocol: STREAMABLE` e ferramenta com anotações de somente leitura.
- [ ] Parâmetros validados em código no servidor.
- [ ] Erros como `RuntimeException` de domínio com mensagem segura (regra da 2.0.1).
- [ ] Endpoint MCP autenticado; credencial só por variável de ambiente.
- [ ] Cliente `SYNC`, `request-timeout` explícito, `toolcallback.enabled=false`.
- [ ] Cabeçalhos de credencial e de trace enviados pelo cliente.
- [ ] Adaptador traduz tipos MCP para `record`s de domínio e falhas para exceções de domínio.
- [ ] Contrato igual a `specs/06-api-contracts.md`.
- [ ] Itens marcados como (conferir) foram confirmados na documentação da versão fixada.
- [ ] Testes de servidor, cliente e contrato executados e passando.
