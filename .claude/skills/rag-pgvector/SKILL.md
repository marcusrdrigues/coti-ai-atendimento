---
name: rag-pgvector
description: Orienta a base de conhecimento da api-conhecimento com Spring AI 2.0.1 e PostgreSQL/pgvector — cadastro de textos, divisão em chunks com sobreposição, embeddings, PgVectorStore, similaritySearch, metadados, fontes, relevância e prevenção de respostas sem evidência. Use ao criar ou alterar cadastro, esquema vetorial, busca ou critérios de relevância.
---

# Skill: RAG com PgVectorStore

Referência: Spring AI 2.0.1. `PgVectorStore`, `SearchRequest`, `TokenTextSplitter` e as propriedades `spring.ai.vectorstore.pgvector.*` foram conferidos na documentação de referência do Spring AI 2.0. Itens marcados como **(conferir)** precisam ser confirmados na documentação da versão fixada.

Atenção: o Javadoc do `PgVectorStore` 2.0 ainda mostra um exemplo antigo (`SearchRequest.query(...).withTopK(...)`). A API atual é o builder: `SearchRequest.builder().query(...).topK(...).similarityThreshold(...).filterExpression(...).build()`.

Requisitos cobertos: RF01, RF02, RF03, RF04, RF11 e RF12 de `specs/02-requirements.md`. Valores entre colchetes são os iniciais configuráveis definidos lá. Nomes de campos, códigos HTTP e erros seguem `specs/06-api-contracts.md` (seções 2, 3 e 4).

| Interface | Contrato |
|---|---|
| `POST /api/conhecimentos` (cadastro) | Seção 3.2 |
| `GET /api/conhecimentos` e `GET /api/conhecimentos/{id}` | Seções 3.3 e 3.4 |
| `POST /api/conhecimentos/diagnostico/busca` (administrativo) | Seção 3.5 |
| Ferramenta MCP `buscar_conhecimento` | Seção 4 |

O caso de uso de busca (`BuscarConhecimentoService`) é **um só**, chamado pela ferramenta MCP e pelo endpoint de diagnóstico; os dois devolvem os mesmos trechos para a mesma pergunta.

## Quando usar

- Implementar ou alterar o cadastro de documentos e o processamento associado.
- Definir ou mudar a divisão em chunks, o modelo de embedding ou a dimensão.
- Criar ou alterar o esquema do banco vetorial (migrações).
- Implementar a busca usada pela ferramenta MCP `buscar_conhecimento`.
- Ajustar `k`, limiar de similaridade, filtros, vigência ou metadados.
- Reindexar a base ou remover documentos (por script no MVP).

Esta skill vale **somente** para a `api-conhecimento` (Artigos 5 e 6). Se você está na `api-atendimentos`, pare: o caminho é `mcp-integration`.

## Responsabilidades

- Transformar documentos autorizados em trechos pesquisáveis, com origem rastreável.
- Responder à busca semântica só com conteúdo publicado, vigente e gerado pelo modelo de embedding atual.
- Devolver evidência suficiente para a `api-atendimentos` citar a fonte e decidir se há conhecimento suficiente.
- Nunca deixar um documento parcialmente publicado.

Esta skill garante os Artigos 5, 6, 9, 10, 11, 12 e 14 da constituição.

## Modelo de dados

Duas estruturas, ambas criadas por migração (Flyway ou Liquibase, conforme `specs/04-plan.md`):

| Tabela | Conteúdo |
|---|---|
| `documento` | id, titulo, fonte, categoria, cadastrado_por (identificador da credencial, nunca o segredo), cadastrado_em, hash_conteudo (único), status, quantidade_chunks, vigente_ate, modelo_embedding. Fonte da verdade sobre o documento. O texto completo é guardado aqui para permitir reindexação. |
| `vector_store` (tabela do `PgVectorStore`) | id do chunk, content, metadata (JSON), embedding (`vector(N)`). Índice HNSW, distância cosseno. |

### Status do documento

| Status | Quando | Tem chunks? | Pesquisável? |
|---|---|---|---|
| `PUBLICADO` | Análise sem pendências, divisão e embeddings concluídos com sucesso | Sim | Sim |
| `EM_REVISAO` | Padrão de prompt injection detectado no cadastro | Não | Não |
| `ERRO_PROCESSAMENTO` | Falha na divisão ou na geração/gravação de embeddings | Não | Não |

Não há status de rascunho nem publicação manual no MVP: o documento sem pendência é publicado no próprio cadastro (RF01). Documento com dado pessoal não recebe status, porque o cadastro é rejeitado (422) e nada é gravado.

Internamente existe também `PROCESSANDO`, que dura só durante o cadastro (ADR-07 de `specs/03-architecture.md`): nem o documento nem seus chunks são pesquisáveis nesse estado, e ele não aparece no contrato público.

### Metadados obrigatórios em cada chunk (RN02.3)

| Chave | Uso |
|---|---|
| `documentoId` | Remoção, rastreio e citação |
| `titulo` | Exibição da fonte |
| `fonte` | Origem legível informada no cadastro |
| `categoria` | `CURSO`, `PROGRAMA`, `TECNOLOGIA`, `MODALIDADE`, `PRE_REQUISITO` ou `INSTITUCIONAL` |
| `ordem` | Posição do chunk no documento |
| `status` | Filtro de publicação na busca: `PROCESSANDO` ao gravar, `PUBLICADO` após a transação de publicação |
| `modeloEmbedding` | Impede misturar vetores de modelos diferentes |
| `vigenteAte` | Inteiro `AAAAMMDD`; `99991231` quando não há vigência (ADR-08) |
| `cadastradoEm` | Auditoria |

Nenhum dado pessoal de aluno entra na base (Artigo 14).

## Passo a passo de implementação

### 1. Dependências e configuração

- `spring-ai-starter-vector-store-pgvector` e `spring-ai-starter-model-openai` (versões pelo BOM 2.0.1), driver PostgreSQL e a ferramenta de migração aprovada.

```yaml
spring:
  datasource:
    url: ${CONHECIMENTO_DB_URL}
    username: ${CONHECIMENTO_DB_USER}
    password: ${CONHECIMENTO_DB_PASSWORD}
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        model: ${OPENAI_EMBEDDING_MODEL}          # decidido em specs/03-architecture.md
        dimensions: ${OPENAI_EMBEDDING_DIMENSIONS} # deve bater com a coluna vector(N)
    vectorstore:
      pgvector:
        initialize-schema: false     # esquema vem da migração, não da aplicação
        schema-validation: true      # valida nomes de schema/tabela
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: ${OPENAI_EMBEDDING_DIMENSIONS}
        remove-existing-vector-store-table: false
```

- A migração cria `CREATE EXTENSION IF NOT EXISTS vector`, a tabela `vector_store` com a mesma estrutura que o `PgVectorStore` espera (**conferir** colunas e tipos na documentação da versão), o índice HNSW e a tabela `documento` com `hash_conteudo` único.
- Mudar o modelo de embedding ou a dimensão exige nova migração e reindexação completa (RN03.3).

### 2. Portas

Na camada de aplicação da `api-conhecimento`:

- `RepositorioDocumentoPort`: gravar documento, atualizar status, buscar por hash.
- `RepositorioVetorialPort`: gravar trechos com embedding, buscar por similaridade com filtro, remover por `documentoId`.
- `DivisorTextoPort`: transformar texto normalizado em trechos com sobreposição (passo 4).
- `AnalisadorConteudoPort`: detectar padrões de injeção e dados pessoais.

O adaptador `PgVectorRepositorioAdapter` usa o `VectorStore`. O caso de uso não conhece `Document` do Spring AI.

No MVP não há `ExtratorTextoPort`: o conteúdo chega como texto no corpo JSON. Ele entra quando o upload de arquivos for aprovado.

### 3. Fluxo do cadastro (RF01 e Artigo 10)

Em ordem, cada passo pode encerrar o cadastro:

1. **Autorização**: credencial administrativa válida; limite de requisições.
2. **Validação** do corpo: tamanhos, categoria, `vigenteAte` futura (RNF05).
3. **Normalização**: UTF-8, remoção de caracteres de controle, quebras de linha e espaços padronizados.
4. **Deduplicação** por `hash_conteudo` do texto normalizado → `409` se já existir.
5. **Análise do conteúdo**:
   - dado pessoal (CPF, e-mail pessoal, telefone pessoal) → `422`, nada é gravado;
   - padrão de injeção ("ignore as instruções", "você agora é", "system prompt", blocos que imitam papéis de chat) → grava o documento como `EM_REVISAO`, **sem** divisão nem embedding, e responde `202`.
6. **Divisão** com o divisor próprio (passo 4); acima de `[500]` trechos → `413`, nada é gravado.
7. **Metadados**: preencha todas as chaves obrigatórias. Id do chunk determinístico (UUID derivado de `documentoId` + `ordem`).
8. **Embeddings e gravação** em lotes de `[100]` trechos (passo 5).
9. **Publicação**: com todos os lotes gravados, atualiza o documento para `PUBLICADO` com `quantidade_chunks` e responde `201`.
10. **Falha em 6 a 9**: nenhum chunk do documento permanece gravado; o documento fica `ERRO_PROCESSAMENTO`; resposta `503` se a causa foi a OpenAI.

### 4. Divisor próprio com sobreposição (RF02)

O `TokenTextSplitter` do Spring AI 2.0 **não** é usado: ele não tem parâmetro de sobreposição (os parâmetros conferidos são `chunkSize`, `minChunkSizeChars`, `minChunkLengthToEmbed`, `maxNumChunks`, `keepSeparator` e `punctuationMarks`), e o RF02 exige sobreposição entre trechos vizinhos. Alternativa descartada: usar o `TokenTextSplitter` e emendar a sobreposição depois, o que mistura duas regras de corte e quebra o determinismo do id por `ordem`.

O divisor é código Java puro (domínio ou aplicação), sem dependência de Spring AI, implementando `DivisorTextoPort`. Algoritmo, com medida em **caracteres**:

1. Parâmetros de configuração: `tamanhoMaximo [1.500]`, `sobreposicao [200]`, `tamanhoMinimo [50]`, `maximoTrechos [500]`. Validação na subida: `0 ≤ sobreposicao < tamanhoMaximo` (RN02.2).
2. Texto com até `tamanhoMaximo` caracteres → um único trecho com o texto inteiro.
3. Caso contrário, a partir da posição `inicio`, considere a janela `[inicio, inicio + tamanhoMaximo)`.
4. Procure o ponto de corte de trás para frente dentro da janela, nesta prioridade: fim de parágrafo (`\n\n`), fim de frase (`.`, `?`, `!` seguidos de espaço ou quebra), quebra de linha, espaço. Só corte no meio de palavra se nada disso existir.
5. Emita o trecho `[inicio, corte)`, sem espaços nas pontas.
6. Próximo `inicio = corte - sobreposicao`, ajustado para frente até o início de palavra (nunca começar no meio de palavra) e garantindo avanço (`novoInicio > inicioAnterior`), para não entrar em laço.
7. Repita até o fim do texto. Descarte o último trecho se tiver menos que `tamanhoMinimo` caracteres úteis e já houver trecho anterior.
8. Mais de `maximoTrechos` → exceção de domínio (`DocumentoGrandeDemaisException`), tratada como `413`.
9. Mesma entrada e mesma configuração → mesmos trechos (RN02.4).

Medir em caracteres é a escolha simples do MVP; o teto de tokens por chamada é garantido pelo tamanho do trecho (1.500 caracteres ficam bem abaixo do limite de entrada dos modelos de embedding da OpenAI). Se o eval de recuperação pedir corte por tokens, a troca fica isolada no adaptador do divisor.

### 5. Embeddings e gravação sem publicação parcial (RF03)

- Grave os lotes com `vectorStore.add(...)`. O `PgVectorStore` gera o embedding de cada `Document` com o `EmbeddingModel` configurado.
- Retry só em erro transitório (timeout, 429, 5xx), com backoff exponencial e no máximo `[2]` novas tentativas; 401/403 sem retry e com alerta de configuração.
- **Atomicidade (ADR-07 de `specs/03-architecture.md`)**:
  1. o documento é gravado como `PROCESSANDO`;
  2. os lotes são gravados com metadado `status = "PROCESSANDO"`, invisíveis para a busca;
  3. com todos os lotes gravados, **uma transação curta** muda o metadado dos chunks para `PUBLICADO` (`jsonb_set` sobre `metadata`; coluna `json`, conferido no spike T031) e o documento para `PUBLICADO`;
  4. em falha, os chunks do documento são apagados por filtro `documentoId` e o documento fica `ERRO_PROCESSAMENTO`.
- Assim as chamadas à OpenAI ficam fora de transação de banco. Descartados: transação envolvendo todos os `add(...)` (fica aberta durante as chamadas à OpenAI) e junção com `documento` na busca (o filtro do `PgVectorStore` não faz junção).
- Uma rotina na subida limpa documentos `PROCESSANDO` há mais de 10 minutos e seus chunks.

### 6. Busca (`similaritySearch`) (RF04)

```java
// Ilustração do formato, não é código da aplicação
var b = new FilterExpressionBuilder();               // org.springframework.ai.vectorstore.filter (conferido)
var filtro = b.and(
        b.and(b.eq("status", "PUBLICADO"), b.eq("modeloEmbedding", modeloAtual)),
        b.gte("vigenteAte", hojeAAAAMMDD))   // inteiro; 99991231 = sem vigência (ADR-08)
        .build();

var requisicao = SearchRequest.builder()
        .query(pergunta)
        .topK(limite)                                        // padrão 5, máximo 10
        .similarityThreshold(propriedades.limiarSimilaridade()) // inicial [0,70]
        .filterExpression(filtro)
        .build();

List<Document> resultados = vectorStore.similaritySearch(requisicao);
```

- Filtro de publicação, modelo e vigência **dentro** da busca (`filterExpression`), nunca em `stream().filter(...)` depois (RN04.2).
- A vigência é comparada como inteiro `AAAAMMDD` para evitar `isNull` e comparação de datas em JSONPath (ADR-08). O teste de integração prova que conteúdo vencido não volta.
- Monte o filtro com o builder de expressões ou valores fixos do código. **Nunca** concatene texto vindo da requisição em uma expressão de filtro em string.
- **Scan iterativo do HNSW.** O filtro e o limiar são aplicados depois do scan do índice (`ef_search` 40) e o índice guarda linhas apagadas até o `VACUUM`. Sem `hnsw.iterative_scan`, a busca pode voltar vazia (medido em `ScoreCossenoIT`). A conexão da aplicação roda `SET hnsw.iterative_scan = strict_order` por `hikari.connection-init-sql`. Depois de remover muitos trechos, rode `VACUUM vector_store`.
- Ordene por similaridade decrescente; limite cada `texto` a 2.000 caracteres e a soma dos textos a 8.000 caracteres, removendo inteiros os de menor similaridade.
- **O embedding recebe só o texto do trecho.** O `OpenAiEmbeddingModel` usa `Document.getFormattedContent(modo)` e o padrão (`EMBED`) acrescenta os metadados ao texto; mesmo com `metadata-mode: NONE` sobra um prefixo de quebras de linha. A gravação define `ContentFormatter` por documento que devolve `getText()` (`PgVectorRepositorioAdapter.SOMENTE_O_TEXTO`). Um dublê de embedding que lê direto `getText()` esconde esse problema: teste o caminho do modelo real contra uma OpenAI falsa (T053).
- Converta cada `Document` em `TrechoConhecimento(id, texto, documentoId, titulo, fonte, categoria, similaridade)`, que vira o `ResultadoBusca` (`trechos`, `total`) do contrato. `similaridade` vem de `Document.getScore()`, que para `COSINE_DISTANCE` é o cosseno (`1 - distância`); o limiar padrão do Spring AI é 0, então o score devolvido fica em [0, 1] (spike T037; limite em 1 por arredondamento). Se o score vier nulo, `similaridade` é `null` e a ordem da busca é mantida.
- `ordem`, `status`, `modeloEmbedding`, `vigenteAte` e `cadastradoEm` são metadados internos do chunk: usados no filtro e na auditoria, **nunca** devolvidos na saída.

### 7. Relevância e prevenção de resposta sem evidência

- `similarityThreshold` é a primeira barreira: abaixo dele, o trecho não existe para a busca (RN04.1).
- A ferramenta devolve a lista (possivelmente vazia) com similaridades. `trechos: []` com `total: 0` é sucesso (RN04.3). A decisão de "conhecimento insuficiente" fica na `api-atendimentos`, em código, antes de chamar o modelo (RF08, Artigo 11).
- Não "complete" resultados com busca textual ou com trechos de baixa relevância só para não devolver vazio.
- Busca híbrida, rerank e ajuste de `k` só entram quando o eval de recuperação mostrar onde falha.

### 8. Consulta administrativa (RF11 e RF12)

- `GET /api/conhecimentos` e `GET /api/conhecimentos/{id}` leem só a tabela `documento` (nunca o `vector_store`). A listagem não devolve `conteudo`; o detalhe devolve `conteudo`, `modeloEmbedding`, `cadastradoPor` e, se `EM_REVISAO`, `pendencias = ["POSSIVEL_PROMPT_INJECTION"]`. Guarde as pendências detectadas no cadastro para isso.
- `POST /api/conhecimentos/diagnostico/busca` chama o mesmo `BuscarConhecimentoService` da ferramenta MCP e acrescenta `limiarSimilaridade`, `modeloEmbedding` e `duracaoMs`. A pergunta vai no corpo e não é registrada em log.
- Todos exigem a chave administrativa (`X-Api-Key`); a chave MCP é recusada.

### 9. Reindexação e remoção (fora do MVP por API)

- Remoção e edição de documento por API estão fora do MVP (recomendadas como RF13). No MVP, são feitas por script administrativo versionado no repositório.
- O script de remoção apaga os chunks pelo filtro `documentoId == X` (`vectorStore.delete(Filter.Expression)`; assinatura conferida no spike T031) e o registro em `documento`, na mesma operação.
- Reindexação (troca de modelo de embedding): reprocessa todos os documentos `PUBLICADO` a partir do texto guardado em `documento`, em tabela nova ou com janela de manutenção; nunca mistura modelos no mesmo índice.
- Documento `EM_REVISAO` corrigido é recadastrado; o registro antigo é removido pelo script.

## Padrões de codificação

- `VectorStore`, `Document` e `EmbeddingModel` só em adaptadores da infraestrutura. O divisor é código puro, sem Spring AI.
- Chaves de metadados e status como constantes ou `enum` em um único lugar; nada de string solta espalhada.
- Parâmetros de divisão, lotes, `topK`, limiar e limite de contexto em `@ConfigurationProperties` (`record` com `@Validated`).
- Esquema por migração versionada; `initialize-schema: false` em todos os ambientes.
- DTOs e objetos de valor como `record`; injeção por construtor.

## Antipadrões proibidos

- Qualquer acesso ao pgvector, ao `VectorStore` ou ao `EmbeddingModel` a partir da `api-atendimentos`.
- Usar o `TokenTextSplitter` para o RF02 (não tem sobreposição).
- `initialize-schema: true` ou `remove-existing-vector-store-table: true` fora de teste descartável.
- Misturar vetores de modelos de embedding diferentes na mesma tabela ou busca.
- Filtrar publicação ou vigência depois da busca vetorial.
- Expressão de filtro montada por concatenação com entrada do usuário.
- Chunk sem `documentoId`, `fonte`, `categoria` ou `modeloEmbedding`.
- Gerar chunks ou embeddings para documento `EM_REVISAO`.
- Documento parcialmente publicado após falha.
- Indexar dado pessoal de aluno.
- Devolver vetor, metadado interno ou conteúdo não publicado.
- Devolver trecho abaixo do limiar para "ter alguma resposta".
- Logar o texto completo de documentos ou consultas.

## Critérios de validação

- Testes unitários do divisor (sem Spring): sobreposição de até `[200]` caracteres entre vizinhos, nenhum trecho acima de `[1.500]`, corte em fim de frase quando possível, documento curto em um trecho, determinismo, limite de `[500]` trechos, configuração inválida (`sobreposicao >= tamanhoMaximo`) recusada na subida, ausência de laço com texto sem espaços.
- Teste de integração com Testcontainers (`pgvector/pgvector` com tag fixa) e migração real: cadastro, busca, falha de embedding e remoção por script.
- O `EmbeddingModel` nos testes é um dublê determinístico (vetores fixos por texto), sem OpenAI real.
- Teste: documento `EM_REVISAO` não gera chunks e nunca aparece na busca.
- Teste: falha no segundo lote de embeddings não deixa chunk gravado e marca `ERRO_PROCESSAMENTO`.
- Teste: documento com `vigenteAte` no passado não aparece na busca.
- Teste: chunk com `modeloEmbedding` diferente do atual nunca aparece.
- Teste: cadastro repetido devolve `409` e não duplica chunks.
- Teste: dado pessoal no conteúdo devolve `422` e nada é gravado.
- Teste: pergunta sem trecho acima do limiar devolve `trechos` vazio e `total` 0; soma dos textos nunca passa de 8.000 caracteres; nenhum `texto` acima de 2.000.
- Teste: a saída não contém `ordem`, `status`, `modeloEmbedding` nem vetor.
- Teste: listagem sem `conteudo` e com filtros e paginação; detalhe de `EM_REVISAO` com `pendencias`; `id` inexistente devolve `404 CONHECIMENTO_NAO_ENCONTRADO`.
- Teste: diagnóstico e ferramenta MCP devolvem os mesmos trechos para a mesma pergunta.
- Eval de recuperação (fora do CI, com embedding real): para cada pergunta do conjunto, o trecho esperado está entre os `k` primeiros. Nota mínima em `specs/07-test-plan.md`.

## Checklist de conclusão

- [ ] Migração cria extensão, tabelas, índice HNSW e `hash_conteudo` único; `initialize-schema: false`.
- [ ] Modelo e dimensão do embedding em configuração e iguais à coluna `vector(N)`.
- [ ] Fluxo de cadastro com os status `PUBLICADO`, `EM_REVISAO` e `ERRO_PROCESSAMENTO`, sem publicação parcial.
- [ ] Divisor próprio com sobreposição, determinístico e testado.
- [ ] Todos os metadados obrigatórios em cada chunk; ids determinísticos.
- [ ] Busca com `SearchRequest.builder()`, `topK`, `similarityThreshold` e filtro de status, modelo e vigência dentro da busca.
- [ ] Limite de contexto de `[8.000]` caracteres aplicado.
- [ ] Nenhum dado pessoal indexado; nenhum texto completo em log.
- [ ] Itens marcados como (conferir) foram confirmados na documentação da versão fixada.
- [ ] Testes de integração executados com Docker ativo; eval de recuperação executado quando divisão, `k`, limiar ou embedding mudaram.
