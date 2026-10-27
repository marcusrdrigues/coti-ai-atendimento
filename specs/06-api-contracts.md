# Contratos das APIs do COTI AI Atendimento

Versão do contrato: **1.0.0**

Este documento é a fonte da verdade para nomes de campos, tipos, códigos HTTP, erros e a ferramenta MCP (Artigo 17 da constituição). Quando ele diverge de exemplos em requisitos ou skills, **vale este documento**. Mudança aqui é feita por PR, com motivo, antes da mudança no código; mudança incompatível exige nova versão.

## 1. Visão geral

| Serviço | Interface | Protocolo | Consumidor | Credencial |
|---|---|---|---|---|
| `api-conhecimento` | `POST /api/conhecimentos` | REST/JSON | Gestor de conteúdo | Chave administrativa |
| `api-conhecimento` | `GET /api/conhecimentos` | REST/JSON | Gestor de conteúdo | Chave administrativa |
| `api-conhecimento` | `GET /api/conhecimentos/{id}` | REST/JSON | Gestor de conteúdo | Chave administrativa |
| `api-conhecimento` | `POST /api/conhecimentos/diagnostico/busca` | REST/JSON | Gestor de conteúdo (diagnóstico) | Chave administrativa |
| `api-conhecimento` | Ferramenta `buscar_conhecimento` em `/mcp` | **MCP** (JSON-RPC 2.0 sobre Streamable HTTP) | `api-atendimentos` | Chave de serviço MCP |
| `api-atendimentos` | `POST /api/atendimentos/perguntar` | REST/JSON | Aluno / canal de atendimento | Nenhuma (limite por IP) |
| `api-atendimentos` | `GET /actuator/health` | REST/JSON | Orquestrador, monitoração | Nenhuma (detalhes só autenticado) |

### 1.1 REST não é MCP

- Os endpoints REST são documentados em OpenAPI 3 e seguem as convenções HTTP deste documento.
- A ferramenta `buscar_conhecimento` **não** é um endpoint REST. Ela é publicada pelo servidor MCP do Spring AI (`protocol: STREAMABLE`) e acessada por um cliente MCP, com as mensagens `initialize`, `tools/list` e `tools/call` do protocolo. Não existe `GET /mcp/buscar_conhecimento` nem equivalente.
- O endpoint de diagnóstico (seção 3.4) é REST, de uso humano e administrativo. A `api-atendimentos` **não** o utiliza e **não** possui a credencial dele.
- A `api-atendimentos` não acessa o PostgreSQL, o pgvector nem o modelo de embedding. Toda informação da base chega a ela pelo resultado da ferramenta MCP.

### 1.2 Credenciais

| Credencial | Cabeçalho | Variável de ambiente | Aceita em |
|---|---|---|---|
| Chave administrativa | `X-Api-Key: <chave>` | `CONHECIMENTO_ADMIN_API_KEY` | Endpoints REST `/api/conhecimentos/**` |
| Chave de serviço MCP | `Authorization: Bearer <chave>` | `CONHECIMENTO_MCP_API_KEY` | Somente `/mcp` |

- As chaves não são intercambiáveis: a chave administrativa é recusada em `/mcp` e a chave MCP é recusada nos endpoints REST.
- Comparação em tempo constante. A chave nunca aparece em log, erro ou resposta.
- O mecanismo exato (filtro próprio ou módulo de segurança MCP) é decidido em `03-architecture.md`; os cabeçalhos acima fazem parte do contrato.

### 1.3 Convenções gerais (REST)

- `Content-Type: application/json; charset=UTF-8` para requisições e respostas de sucesso; `application/problem+json` para erros.
- Nomes de campos em `camelCase`, em português.
- Datas e horas em ISO-8601 com fuso (`2026-10-22T14:30:00-03:00`); datas sem hora em `AAAA-MM-DD`.
- Identificadores são UUID (string).
- Campos desconhecidos na requisição são rejeitados com `400`.
- Corpo acima de **256 KB** é rejeitado com `413` antes da desserialização.
- Todo response contém o cabeçalho `X-Correlation-Id` (igual ao trace id). Se o cliente enviar `X-Correlation-Id` válido (UUID), ele é ignorado para o trace e apenas registrado como referência do cliente.
- Campos opcionais ausentes são omitidos da resposta, não enviados como `null`, exceto quando indicado.

---

## 2. Resposta de erro padronizada

Todos os erros REST das duas APIs usam **Problem Details (RFC 9457)** com extensões fixas.

### 2.1 Formato

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `type` | string (URI) | Sim | `urn:coti-ai:problema:<codigo-em-kebab-case>` |
| `title` | string | Sim | Título curto e estável do problema |
| `status` | integer | Sim | Código HTTP |
| `detail` | string | Sim | Mensagem segura para o usuário, em português |
| `instance` | string | Sim | Caminho da requisição (sem query string) |
| `codigo` | string | Sim | Código estável da tabela 2.2 |
| `correlacaoId` | string (UUID) | Sim | Igual ao `X-Correlation-Id` |
| `timestamp` | string (data-hora) | Sim | Momento do erro |
| `erros` | array de `ErroCampo` | Só em `DADOS_INVALIDOS` | Lista de campos inválidos |

`ErroCampo`:

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `campo` | string | Sim | Caminho do campo (`titulo`, `fontes[0].nome`) |
| `mensagem` | string | Sim | Regra violada, **sem ecoar o valor recebido** |

Exemplo:

```json
{
  "type": "urn:coti-ai:problema:dados-invalidos",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "A requisição contém campos inválidos.",
  "instance": "/api/conhecimentos",
  "codigo": "DADOS_INVALIDOS",
  "correlacaoId": "7b8f1c2e-3a4d-4e5f-9a6b-1c2d3e4f5a6b",
  "timestamp": "2026-10-22T14:30:00-03:00",
  "erros": [
    { "campo": "conteudo", "mensagem": "deve ter entre 50 e 100000 caracteres" },
    { "campo": "categoria", "mensagem": "valor não permitido" }
  ]
}
```

### 2.2 Catálogo de códigos

| `codigo` | HTTP | Onde | `detail` padrão |
|---|---|---|---|
| `DADOS_INVALIDOS` | 400 | Ambas | A requisição contém campos inválidos. |
| `NAO_AUTENTICADO` | 401 | Ambas | Credencial ausente ou inválida. |
| `ACESSO_NEGADO` | 403 | `api-conhecimento` | A credencial não tem permissão para esta operação. |
| `CONHECIMENTO_NAO_ENCONTRADO` | 404 | `api-conhecimento` | Conhecimento não encontrado. |
| `RECURSO_NAO_ENCONTRADO` | 404 | Ambas | Recurso não encontrado. |
| `METODO_NAO_PERMITIDO` | 405 | Ambas | Método não permitido para este recurso. |
| `CONHECIMENTO_DUPLICADO` | 409 | `api-conhecimento` | Já existe um conhecimento com o mesmo conteúdo. |
| `CONTEUDO_GRANDE_DEMAIS` | 413 | Ambas | O conteúdo excede o tamanho permitido. |
| `TIPO_MIDIA_NAO_SUPORTADO` | 415 | Ambas | Use application/json. |
| `DADO_PESSOAL_DETECTADO` | 422 | `api-conhecimento` | O conteúdo contém dados pessoais e não pode ser cadastrado. |
| `LIMITE_REQUISICOES` | 429 | Ambas | Muitas requisições. Tente novamente mais tarde. |
| `ERRO_INTERNO` | 500 | Ambas | Ocorreu um erro inesperado. Informe o correlacaoId ao suporte. |
| `MODELO_INDISPONIVEL` | 503 | Ambas | O serviço de inteligência artificial está temporariamente indisponível. |
| `BASE_CONHECIMENTO_INDISPONIVEL` | 503 | Ambas | A base de conhecimento está temporariamente indisponível. |
| `TEMPO_ESGOTADO` | 503 | `api-atendimentos` | Não foi possível concluir o atendimento a tempo. Tente novamente. |

Regras:
- `429` e `503` incluem o cabeçalho `Retry-After` (segundos).
- O `detail` nunca contém stack trace, nome de classe, SQL, URL interna, nome de host, chave, modelo de IA nem o valor recebido.
- `500` é sempre genérico; a causa fica só no log interno, ligada ao `correlacaoId`.

---

## 3. api-conhecimento — REST

Recurso: **conhecimento** = documento textual oficial da COTI (RF01). Requisitos: RF01 a RF04.

### 3.1 Tipos comuns

`Categoria` (enum string): `CURSO`, `PROGRAMA`, `TECNOLOGIA`, `MODALIDADE`, `PRE_REQUISITO`, `INSTITUCIONAL`.

`StatusConhecimento` (enum string):

| Valor | Significado |
|---|---|
| `PUBLICADO` | Processado com sucesso; seus trechos são pesquisáveis. |
| `EM_REVISAO` | Retido por possível prompt injection; sem trechos; não pesquisável. |
| `ERRO_PROCESSAMENTO` | Falha na divisão ou nos embeddings; sem trechos; não pesquisável. |

`Pendencia` (enum string): `POSSIVEL_PROMPT_INJECTION`.

### 3.2 `POST /api/conhecimentos` — cadastrar conhecimento (RF01, RF02, RF03)

**Autenticação:** chave administrativa. **Limite:** `[30]` requisições por minuto por credencial.

**Requisição — `CadastrarConhecimentoRequest`**

| Campo | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `titulo` | string | Sim | 3 a 200 caracteres, sem espaços nas pontas |
| `conteudo` | string | Sim | Texto puro ou Markdown, 50 a 100.000 caracteres |
| `fonte` | string | Sim | 3 a 300 caracteres; origem legível |
| `categoria` | `Categoria` | Sim | Valor do enum |
| `vigenteAte` | string (data) | Não | `AAAA-MM-DD`, igual ou posterior à data atual |

```json
{
  "titulo": "Curso Java Fullstack — pré-requisitos",
  "conteudo": "## Pré-requisitos\n\nPara o curso Java Fullstack é recomendado conhecimento básico de lógica de programação...",
  "fonte": "Site COTI — página do curso Java Fullstack, out/2026",
  "categoria": "PRE_REQUISITO",
  "vigenteAte": "2027-06-30"
}
```

**Resposta `201 Created` — `ConhecimentoCadastradoResponse`** (cabeçalho `Location: /api/conhecimentos/{id}`)

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `id` | string (UUID) | Sim | Identificador do conhecimento |
| `status` | `StatusConhecimento` | Sim | `PUBLICADO` |
| `quantidadeChunks` | integer | Sim | Maior que zero |
| `modeloEmbedding` | string | Sim | Modelo usado na indexação |
| `cadastradoEm` | string (data-hora) | Sim | |

```json
{
  "id": "3f6c2a1e-8b4d-4c7a-9e1f-2b3c4d5e6f70",
  "status": "PUBLICADO",
  "quantidadeChunks": 4,
  "modeloEmbedding": "text-embedding-3-small",
  "cadastradoEm": "2026-10-22T14:30:00-03:00"
}
```

**Resposta `202 Accepted`** (retido para revisão; mesmo DTO, com `quantidadeChunks = 0` e `pendencias`)

```json
{
  "id": "9a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
  "status": "EM_REVISAO",
  "quantidadeChunks": 0,
  "modeloEmbedding": "text-embedding-3-small",
  "cadastradoEm": "2026-10-22T14:31:00-03:00",
  "pendencias": ["POSSIVEL_PROMPT_INJECTION"]
}
```

**Erros:** `400 DADOS_INVALIDOS`, `401 NAO_AUTENTICADO`, `403 ACESSO_NEGADO`, `409 CONHECIMENTO_DUPLICADO`, `413 CONTEUDO_GRANDE_DEMAIS` (corpo acima de 256 KB ou mais de 500 trechos), `415`, `422 DADO_PESSOAL_DETECTADO`, `429`, `503 MODELO_INDISPONIVEL` (embeddings falharam; o conhecimento fica `ERRO_PROCESSAMENTO`), `503 BASE_CONHECIMENTO_INDISPONIVEL` (banco fora).

Em `503 MODELO_INDISPONIVEL`, o corpo inclui a extensão `conhecimentoId` para que o gestor possa consultar o registro com status `ERRO_PROCESSAMENTO`.

### 3.3 `GET /api/conhecimentos` — listar conhecimentos

**Autenticação:** chave administrativa.

**Parâmetros de consulta**

| Parâmetro | Tipo | Obrigatório | Padrão | Regras |
|---|---|---|---|---|
| `status` | `StatusConhecimento` | Não | todos | |
| `categoria` | `Categoria` | Não | todas | |
| `pagina` | integer | Não | `0` | ≥ 0 |
| `tamanho` | integer | Não | `20` | 1 a 100 |

Ordenação fixa: `cadastradoEm` decrescente.

**Resposta `200 OK` — `PaginaConhecimentosResponse`**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `itens` | array de `ConhecimentoResumo` | Sim | Pode ser vazio |
| `pagina` | integer | Sim | |
| `tamanho` | integer | Sim | |
| `totalItens` | integer (long) | Sim | |
| `totalPaginas` | integer | Sim | |

`ConhecimentoResumo`: `id`, `titulo`, `fonte`, `categoria`, `status`, `quantidadeChunks`, `vigenteAte` (opcional), `cadastradoEm`. **Não** contém `conteudo`.

```json
{
  "itens": [
    {
      "id": "3f6c2a1e-8b4d-4c7a-9e1f-2b3c4d5e6f70",
      "titulo": "Curso Java Fullstack — pré-requisitos",
      "fonte": "Site COTI — página do curso Java Fullstack, out/2026",
      "categoria": "PRE_REQUISITO",
      "status": "PUBLICADO",
      "quantidadeChunks": 4,
      "vigenteAte": "2027-06-30",
      "cadastradoEm": "2026-10-22T14:30:00-03:00"
    }
  ],
  "pagina": 0,
  "tamanho": 20,
  "totalItens": 1,
  "totalPaginas": 1
}
```

**Erros:** `400` (parâmetro inválido), `401`, `403`, `429`, `503 BASE_CONHECIMENTO_INDISPONIVEL`.

### 3.4 `GET /api/conhecimentos/{id}` — detalhar conhecimento

**Autenticação:** chave administrativa. `id` deve ser UUID válido (senão `400`).

**Resposta `200 OK` — `ConhecimentoDetalheResponse`**: todos os campos de `ConhecimentoResumo`, mais:

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `conteudo` | string | Sim | Texto normalizado cadastrado |
| `modeloEmbedding` | string | Sim | |
| `pendencias` | array de `Pendencia` | Não | Presente quando `EM_REVISAO` |
| `cadastradoPor` | string | Sim | Identificador da credencial (nunca o segredo) |

```json
{
  "id": "3f6c2a1e-8b4d-4c7a-9e1f-2b3c4d5e6f70",
  "titulo": "Curso Java Fullstack — pré-requisitos",
  "conteudo": "## Pré-requisitos\n\nPara o curso Java Fullstack é recomendado conhecimento básico de lógica de programação...",
  "fonte": "Site COTI — página do curso Java Fullstack, out/2026",
  "categoria": "PRE_REQUISITO",
  "status": "PUBLICADO",
  "quantidadeChunks": 4,
  "modeloEmbedding": "text-embedding-3-small",
  "vigenteAte": "2027-06-30",
  "cadastradoEm": "2026-10-22T14:30:00-03:00",
  "cadastradoPor": "admin-conteudo"
}
```

**Erros:** `400`, `401`, `403`, `404 CONHECIMENTO_NAO_ENCONTRADO`, `429`, `503 BASE_CONHECIMENTO_INDISPONIVEL`.

### 3.5 `POST /api/conhecimentos/diagnostico/busca` — busca semântica de diagnóstico

Uso: o gestor verifica o que a busca devolve para uma pergunta (investigar respostas, montar casos de eval). Executa **o mesmo caso de uso** da ferramenta MCP (RF04), com os mesmos filtros (só `PUBLICADO`, vigente e do modelo atual) e o mesmo limiar.

- Método `POST` para que a pergunta não vá para URL, logs de acesso ou histórico.
- **Autenticação:** chave administrativa. **Limite:** `[10]` requisições por minuto por credencial.
- **Não** é usado pela `api-atendimentos` e não é exposto fora da rede administrativa em produção.

**Requisição — `BuscaDiagnosticoRequest`**

| Campo | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `pergunta` | string | Sim | 3 a 1.000 caracteres |
| `limite` | integer | Não | Padrão 5; 1 a 10 |

```json
{ "pergunta": "Preciso saber programar antes do curso de Java?", "limite": 3 }
```

**Resposta `200 OK` — `BuscaDiagnosticoResponse`**: o mesmo `ResultadoBusca` da ferramenta MCP (`outputSchema` da seção 4.2), acrescido de:

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `limiarSimilaridade` | number | Sim | Limiar aplicado |
| `modeloEmbedding` | string | Sim | Modelo usado na consulta |
| `duracaoMs` | integer | Sim | Tempo da busca |

```json
{
  "trechos": [
    {
      "id": "c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f",
      "texto": "Para o curso Java Fullstack é recomendado conhecimento básico de lógica de programação...",
      "documentoId": "3f6c2a1e-8b4d-4c7a-9e1f-2b3c4d5e6f70",
      "titulo": "Curso Java Fullstack — pré-requisitos",
      "fonte": "Site COTI — página do curso Java Fullstack, out/2026",
      "categoria": "PRE_REQUISITO",
      "similaridade": 0.86
    }
  ],
  "total": 1,
  "limiarSimilaridade": 0.70,
  "modeloEmbedding": "text-embedding-3-small",
  "duracaoMs": 142
}
```

**Erros:** `400`, `401`, `403`, `429`, `503 MODELO_INDISPONIVEL`, `503 BASE_CONHECIMENTO_INDISPONIVEL`.

---

## 4. api-conhecimento — ferramenta MCP `buscar_conhecimento`

Requisitos: RF04 e RF05.

### 4.1 Transporte e servidor

| Item | Valor |
|---|---|
| Protocolo | Model Context Protocol, mensagens JSON-RPC 2.0 |
| Transporte | Streamable HTTP (Spring AI `spring.ai.mcp.server.protocol=STREAMABLE`) |
| Endpoint | `/mcp` |
| Nome do servidor | `coti-conhecimento` |
| Versão do servidor | `1.0.0` (acompanha a versão deste contrato) |
| Autenticação | `Authorization: Bearer <CONHECIMENTO_MCP_API_KEY>` em toda requisição HTTP; sem credencial válida → HTTP `401` antes de qualquer processamento MCP |
| Exposição | Somente na rede interna dos serviços |
| Ferramentas publicadas | Apenas `buscar_conhecimento` (somente leitura) |

### 4.2 Descrição da ferramenta (`tools/list`)

| Atributo | Valor |
|---|---|
| `name` | `buscar_conhecimento` |
| `title` | `Buscar conhecimento COTI` |
| `description` | `Busca trechos publicados da base de conhecimento oficial da COTI Informática semanticamente relacionados à pergunta.` |
| `annotations` | `readOnlyHint: true`, `destructiveHint: false`, `idempotentHint: true`, `openWorldHint: false` |

**`inputSchema`**

```json
{
  "type": "object",
  "properties": {
    "pergunta": {
      "type": "string",
      "description": "Pergunta ou texto a pesquisar na base de conhecimento.",
      "minLength": 3,
      "maxLength": 1000
    },
    "limite": {
      "type": "integer",
      "description": "Quantidade máxima de trechos a devolver.",
      "minimum": 1,
      "maximum": 10,
      "default": 5
    }
  },
  "required": ["pergunta"],
  "additionalProperties": false
}
```

**`outputSchema`** (`ResultadoBusca`)

```json
{
  "type": "object",
  "properties": {
    "trechos": {
      "type": "array",
      "maxItems": 10,
      "items": {
        "type": "object",
        "properties": {
          "id":           { "type": "string", "format": "uuid", "description": "Identificador do chunk" },
          "texto":        { "type": "string", "maxLength": 2000 },
          "documentoId":  { "type": "string", "format": "uuid" },
          "titulo":       { "type": "string" },
          "fonte":        { "type": "string" },
          "categoria":    { "type": "string", "enum": ["CURSO", "PROGRAMA", "TECNOLOGIA", "MODALIDADE", "PRE_REQUISITO", "INSTITUCIONAL"] },
          "similaridade": { "type": ["number", "null"], "minimum": 0, "maximum": 1 }
        },
        "required": ["id", "texto", "documentoId", "titulo", "fonte", "categoria"]
      }
    },
    "total": { "type": "integer", "minimum": 0 }
  },
  "required": ["trechos", "total"]
}
```

O schema é gerado pelo Spring AI a partir do `record` de retorno (`generateOutputSchema = true`). O teste de contrato compara o schema publicado com este.

**Diferenças do schema publicado (Spring AI 2.0.1, verificadas em `BuscarConhecimentoToolContratoIT`).** Os schemas acima são o contrato lógico; o que o servidor publica em `tools/list` tem tipo, descrição, `format` (`uuid`, `int32`, `double`), `enum` da categoria e `required` iguais, mas **não** traz as restrições que a anotação `@McpToolParam` e o gerador não sabem expressar: `minLength`, `maxLength`, `minimum`, `maximum`, `default`, `maxItems` e `additionalProperties: false`; `similaridade` é `number` (não `["number","null"]`) e não é obrigatória. As regras valem no servidor, independentemente do schema (§4.3): `pergunta` de 3 a 1.000 caracteres, `limite` de 1 a 10 (padrão 5), no máximo 10 trechos, texto de até 2.000 caracteres. Argumento desconhecido é ignorado.

### 4.3 Regras da ferramenta

- `pergunta` e `limite` são validados **no servidor**, independentemente do schema: `pergunta` sem espaços nas pontas, 3 a 1.000 caracteres; `limite` ausente vira 5; fora de 1 a 10 é erro.
- A busca aplica, dentro da consulta vetorial: `status = PUBLICADO`, modelo de embedding atual, vigência válida, `topK = limite` e limiar de similaridade `[0,70]`.
- `trechos` vem ordenado por `similaridade` decrescente.
- A soma de `texto` de todos os trechos é limitada a **8.000 caracteres**; os de menor similaridade são removidos para respeitar o limite. Cada `texto` tem no máximo 2.000 caracteres.
- `similaridade` é o score de 0 a 1 calculado pela busca. É `null` apenas se o mecanismo de busca não fornecer score; nesse caso a ordem devolvida é a ordem de relevância da busca.
- `total` é a quantidade de itens em `trechos`.
- Lista vazia (`trechos: []`, `total: 0`) é **sucesso**, não erro.
- A saída nunca contém vetor, metadados internos, status, modelo de embedding ou trechos não publicados.

### 4.4 Exemplo de chamada (`tools/call`)

Requisição HTTP (corpo JSON-RPC), após a inicialização da sessão MCP:

```http
POST /mcp HTTP/1.1
Authorization: Bearer ${CONHECIMENTO_MCP_API_KEY}
Content-Type: application/json
Accept: application/json, text/event-stream
Mcp-Session-Id: <id da sessão devolvido no initialize>
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
```

```json
{
  "jsonrpc": "2.0",
  "id": 12,
  "method": "tools/call",
  "params": {
    "name": "buscar_conhecimento",
    "arguments": { "pergunta": "As aulas do curso de Angular são online?", "limite": 5 }
  }
}
```

Resposta de sucesso:

```json
{
  "jsonrpc": "2.0",
  "id": 12,
  "result": {
    "structuredContent": {
      "trechos": [
        {
          "id": "e7f8a9b0-c1d2-4e3f-8a4b-5c6d7e8f9a0b",
          "texto": "O curso de Angular é oferecido nas modalidades presencial e online ao vivo...",
          "documentoId": "5d6e7f8a-9b0c-4d1e-8f2a-3b4c5d6e7f8a",
          "titulo": "Modalidades dos cursos",
          "fonte": "Manual do aluno COTI 2026, seção 2",
          "categoria": "MODALIDADE",
          "similaridade": 0.83
        }
      ],
      "total": 1
    },
    "content": [
      { "type": "text", "text": "{\"trechos\":[...],\"total\":1}" }
    ],
    "isError": false
  }
}
```

- `structuredContent` é a fonte para o cliente. O `content` com o JSON serializado existe por compatibilidade com clientes MCP antigos e deve ser equivalente.
- Os cabeçalhos exatos de sessão e negociação (`Mcp-Session-Id`, `Accept`) são tratados pelos SDKs do Spring AI/MCP; o exemplo é ilustrativo do protocolo, não algo a ser montado à mão.

### 4.5 Erros da ferramenta

| Situação | Como chega ao cliente | Conteúdo |
|---|---|---|
| Sem credencial ou credencial inválida | HTTP `401` (sem JSON-RPC) | — |
| Argumentos inválidos | `result.isError = true` | `content[0].text` = JSON `ErroFerramenta` com `codigo = ARGUMENTOS_INVALIDOS` |
| Banco indisponível | `result.isError = true` | `codigo = BASE_CONHECIMENTO_INDISPONIVEL` |
| Embedding da pergunta indisponível | `result.isError = true` | `codigo = MODELO_INDISPONIVEL` |
| Erro inesperado | `result.isError = true` | `codigo = ERRO_INTERNO` |
| Ferramenta inexistente / JSON-RPC malformado | erro JSON-RPC (`error.code`, por exemplo `-32602`) | definido pelo SDK |

`ErroFerramenta` (serializado como texto no `content`):

```json
{ "codigo": "ARGUMENTOS_INVALIDOS", "mensagem": "O campo 'limite' deve estar entre 1 e 10." }
```

- `mensagem` é segura: sem SQL, stack trace, nome de classe ou valor recebido.
- O `content[0].text` é o JSON `ErroFerramenta` seguido de uma quebra de linha (o Spring AI 2.0.1 acrescenta a mensagem da causa, que é vazia de propósito). Quem consome deve fazer `strip()` antes de interpretar.
- **Violação do schema.** Tipo errado (`limite` como texto) ou `pergunta` ausente é recusado pelo SDK antes da ferramenta: `isError = true`, sem o valor recebido, mas o texto é uma mensagem do SDK, **não** o JSON `ErroFerramenta`. Quem consome trata todo `isError = true` como falha e só interpreta o `codigo` quando o texto for um JSON `ErroFerramenta`. Valores fora da faixa (`limite` 0 ou 11, `pergunta` curta ou longa) chegam à ferramenta e devolvem `ARGUMENTOS_INVALIDOS` normalmente.
- Implementação: erros são `RuntimeException` de domínio lançadas pelo método `@McpTool`, que o Spring AI 2.0.1 converte em `isError = true` (ver skill `mcp-integration`).

---

## 5. api-atendimentos — REST

Requisitos: RF06 a RF10.

### 5.1 `POST /api/atendimentos/perguntar` — fazer uma pergunta

**Autenticação:** nenhuma no MVP. **Limite:** `[10]` requisições por minuto por IP.

**Requisição — `PerguntarRequest`**

| Campo | Tipo | Obrigatório | Regras |
|---|---|---|---|
| `pergunta` | string | Sim | 3 a 1.000 caracteres após remover espaços nas pontas |

```json
{ "pergunta": "Preciso saber lógica de programação para fazer o curso de Java?" }
```

**Resposta `200 OK` — `RespostaAtendimentoResponse`**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `atendimentoId` | string (UUID) | Sim | Igual ao `X-Correlation-Id` |
| `resposta` | string | Sim | Texto ao aluno |
| `conhecimentoSuficiente` | boolean | Sim | `false` quando não há evidência |
| `fontes` | array de `FonteResponse` | Sim | Vazio quando `conhecimentoSuficiente = false` |
| `geradoEm` | string (data-hora) | Sim | |

`FonteResponse`:

| Campo | Tipo | Obrigatório | Origem |
|---|---|---|---|
| `id` | string (UUID) | Sim | `trechos[].id` do resultado MCP |
| `documentoId` | string (UUID) | Sim | `trechos[].documentoId` |
| `titulo` | string | Sim | `trechos[].titulo` |
| `fonte` | string | Sim | `trechos[].fonte` |
| `categoria` | `Categoria` | Sim | `trechos[].categoria` |
| `similaridade` | number ou `null` | Sim | `trechos[].similaridade` |

**Todos os campos de `FonteResponse` são copiados do resultado da ferramenta MCP.** O modelo de IA nunca produz esses valores (seção 6.4).

Exemplo com evidência:

```json
{
  "atendimentoId": "7b8f1c2e-3a4d-4e5f-9a6b-1c2d3e4f5a6b",
  "resposta": "Sim. Para o curso Java Fullstack é recomendado ter conhecimento básico de lógica de programação.",
  "conhecimentoSuficiente": true,
  "fontes": [
    {
      "id": "c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f",
      "documentoId": "3f6c2a1e-8b4d-4c7a-9e1f-2b3c4d5e6f70",
      "titulo": "Curso Java Fullstack — pré-requisitos",
      "fonte": "Site COTI — página do curso Java Fullstack, out/2026",
      "categoria": "PRE_REQUISITO",
      "similaridade": 0.86
    }
  ],
  "geradoEm": "2026-10-22T14:32:05-03:00"
}
```

Exemplo sem evidência (RF10, `200`):

```json
{
  "atendimentoId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "resposta": "Não encontrei essa informação nos materiais oficiais da COTI. Fale com a nossa equipe pelo canal de atendimento: https://www.coti.com.br/contato",
  "conhecimentoSuficiente": false,
  "fontes": [],
  "geradoEm": "2026-10-22T14:33:10-03:00"
}
```

O texto de recusa e o canal humano vêm de configuração (`ATENDIMENTO_MENSAGEM_SEM_EVIDENCIA` e `ATENDIMENTO_CANAL_HUMANO`); o exemplo é ilustrativo. A `resposta` é a mensagem seguida de um espaço e do canal humano.

**Erros:** `400 DADOS_INVALIDOS`, `413`, `415`, `429 LIMITE_REQUISICOES`, `500 ERRO_INTERNO`, `503 BASE_CONHECIMENTO_INDISPONIVEL`, `503 MODELO_INDISPONIVEL`, `503 TEMPO_ESGOTADO`. Todos no formato da seção 2, com `correlacaoId` igual ao `atendimentoId` do atendimento que falhou.

**Nunca** fazem parte da resposta: prompt, nome do modelo, tokens, texto integral dos trechos, trechos não citados.

### 5.2 `GET /actuator/health` — saúde do serviço

**Autenticação:** nenhuma para o status agregado; detalhes de componentes somente com credencial de monitoração (`show-details: when-authorized`).

| Endpoint | Uso | Inclui dependência externa? |
|---|---|---|
| `GET /actuator/health` | Status agregado | Sim (`conhecimentoMcp`) |
| `GET /actuator/health/liveness` | Processo vivo (reinício do container) | Não |
| `GET /actuator/health/readiness` | Pronto para receber tráfego | Não (o serviço atende mesmo sem a `api-conhecimento`, devolvendo RF10) |

Resposta pública (`200 OK`):

```json
{ "status": "UP" }
```

Resposta autenticada (`200 OK`), com a `api-conhecimento` indisponível:

```json
{
  "status": "DEGRADADO",
  "components": {
    "conhecimentoMcp": { "status": "DEGRADADO", "details": { "codigo": "BASE_CONHECIMENTO_INDISPONIVEL" } },
    "ping": { "status": "UP" }
  }
}
```

| Status agregado | HTTP | Quando |
|---|---|---|
| `UP` | 200 | Tudo disponível |
| `DEGRADADO` | 200 | `api-conhecimento` inacessível; o serviço responde, mas só com `503 BASE_CONHECIMENTO_INDISPONIVEL` |
| `DOWN` | 503 | Falha do próprio serviço |

- O indicador `conhecimentoMcp` faz `ping` ou `tools/list` no servidor MCP com timeout de 2 s e confere que `buscar_conhecimento` existe. **Não** chama a OpenAI.
- `DEGRADADO` é um status customizado do Spring Boot Actuator com ordem e mapeamento HTTP configurados (decisão em `03-architecture.md`).
- O componente `conhecimentoMcp` reporta `DEGRADADO` (e não `DOWN`) quando a base está fora: o Actuator agrega pelo pior status e um componente `DOWN` tornaria o agregado `DOWN` (503). Por isso o exemplo acima mostra `DEGRADADO` no componente. O resultado da verificação fica em cache por alguns segundos, para o endpoint público não virar uma chamada ao MCP por requisição.
- Os detalhes nunca expõem URL, host, porta ou credencial.

A `api-conhecimento` também expõe `/actuator/health` (com o indicador do banco), com as mesmas regras de exposição.

---

## 6. Como a api-atendimentos transforma o resultado MCP em contexto RAG

Requisitos: RF07, RF08, RF09 e RF10.

### 6.1 Sequência

```
PerguntarRequest
  → validar e mascarar dados pessoais (RF06)
  → tools/call buscar_conhecimento { pergunta: <mascarada>, limite: 5 }   (RF07)
  → validar ResultadoBusca (6.2)
  → montar contexto (6.3)
      sem trechos válidos → resposta SEM EVIDÊNCIA, modelo NÃO é chamado
  → chamar o modelo com o contexto e o schema de saída (6.4)
  → validar a saída e montar as fontes a partir do ResultadoBusca (6.4, 6.5)
  → RespostaAtendimentoResponse
```

### 6.2 Validação do resultado MCP

O resultado é rejeitado por inteiro (falha `BASE_CONHECIMENTO_INDISPONIVEL`) se:
- `isError = true`;
- `structuredContent` ausente ou não conforme o `outputSchema` (campo obrigatório ausente, tipo errado, categoria desconhecida, `similaridade` fora de 0 a 1);
- mais de 10 trechos ou `id` repetido.

Nada do resultado inválido é usado parcialmente.

### 6.3 Montagem do contexto

1. **Filtrar**: descartar trechos com `similaridade` presente e abaixo do limiar configurado na `api-atendimentos` (`[0,70]`, igual ao do servidor; defesa em profundidade).
2. **Ordenar** por `similaridade` decrescente (ausente vai para o fim, mantendo a ordem do servidor).
3. **Sanitizar** `texto`, `titulo` e `fonte`:
   - remover caracteres de controle;
   - neutralizar sequências que imitam os delimitadores do prompt (`<trechos>`, `</trechos>`, `<trecho`, `</trecho>`, `<pergunta>`, `</pergunta>`), substituindo `<` e `>` por `‹` e `›` nesses casos;
   - escapar aspas em `titulo`, `fonte` e `categoria`, que vão em atributos.
4. **Limitar tamanho**:
   - cada `texto` até **2.000** caracteres (corte em fim de frase quando possível);
   - soma dos `texto` até **8.000** caracteres; ao exceder, remover os trechos de menor similaridade inteiros (não cortar o último pela metade);
   - no máximo **5** trechos (igual ao `limite` enviado).
5. **Rotular**: atribuir a cada trecho mantido uma referência local sequencial `T1`, `T2`, ... e guardar em memória o mapa `referência → trecho MCP`. O modelo vê só a referência, nunca o UUID.
6. **Sem trechos após 1 a 4** → resposta **sem evidência** (seção 5.1, `200`, `conhecimentoSuficiente = false`), sem chamar o modelo.

Formato do bloco de contexto no prompt (o texto completo do prompt fica em arquivo versionado, skill `spring-ai`):

```text
<trechos>
<trecho ref="T1" titulo="Curso Java Fullstack — pré-requisitos" fonte="Site COTI — página do curso Java Fullstack, out/2026" categoria="PRE_REQUISITO">
Para o curso Java Fullstack é recomendado conhecimento básico de lógica de programação...
</trecho>
<trecho ref="T2" titulo="..." fonte="..." categoria="...">
...
</trecho>
</trechos>
<pergunta>
Preciso saber lógica de programação para fazer o curso de Java?
</pergunta>
```

**Orçamento total do prompt:** instruções do sistema + contexto (≤ 8.000 caracteres) + pergunta (≤ 1.000 caracteres). Saída limitada a `[600]` tokens. O contexto nunca recebe o resultado MCP bruto.

### 6.4 Saída do modelo e derivação das fontes

O modelo responde com saída estruturada (schema JSON nativo da OpenAI), **interna** à `api-atendimentos` (não é contrato público):

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `resposta` | string | Sim | Texto ao aluno, em português |
| `conhecimentoSuficiente` | boolean | Sim | |
| `referencias` | array de string | Sim | Referências `T<n>` usadas; vazio se `conhecimentoSuficiente = false` |

```json
{ "resposta": "Sim. Para o curso Java Fullstack é recomendado ...", "conhecimentoSuficiente": true, "referencias": ["T1"] }
```

Validação em código, nesta ordem:
1. JSON conforme o schema.
2. Toda referência existe no mapa da seção 6.3, passo 5. Referência desconhecida → saída reprovada.
3. `conhecimentoSuficiente = true` exige ao menos uma referência.
4. Valores monetários, datas, horários, número de vagas e condições comerciais presentes em `resposta` aparecem literalmente no `texto` de algum trecho referenciado (RF08).

Saída reprovada → reenvio ao modelo com o motivo da reprovação, no máximo **2** vezes; persistindo → resposta **sem evidência**.

**Derivação das fontes:** para cada referência válida, sem repetição, `FonteResponse` é copiada do trecho MCP correspondente (`id`, `documentoId`, `titulo`, `fonte`, `categoria`, `similaridade`), em ordem de similaridade decrescente. Nenhum campo de fonte vem do texto gerado pelo modelo. Trechos não referenciados não aparecem.

Se o modelo devolver `conhecimentoSuficiente = false`, a resposta final é a mensagem de recusa configurada (não o texto do modelo), com `fontes = []`.

### 6.5 Comportamento em ausência de resultados, erros e timeouts

| Situação | Tentativas | Resultado HTTP | `codigo` |
|---|---|---|---|
| `trechos` vazio ou todos abaixo do limiar | — | `200`, sem evidência | — |
| Modelo indica conhecimento insuficiente | — | `200`, sem evidência | — |
| Saída do modelo reprovada após 2 novas tentativas | 2 | `200`, sem evidência | — |
| MCP: timeout (`[5 s]`), conexão recusada ou outra falha de transporte (HTTP 5xx, sessão perdida porque a api-conhecimento reiniciou) | até 2 novas, backoff exponencial com variação | `503` | `BASE_CONHECIMENTO_INDISPONIVEL` |
| MCP: HTTP 401/403 | nenhuma (alerta de configuração no log) | `503` | `BASE_CONHECIMENTO_INDISPONIVEL` |
| MCP: `isError = true` ou resultado fora do schema | nenhuma | `503` | `BASE_CONHECIMENTO_INDISPONIVEL` |
| OpenAI: timeout (`[20 s]`), 429 ou 5xx | 1 nova | `503` | `MODELO_INDISPONIVEL` |
| OpenAI: 400/401/403 | nenhuma (alerta de configuração no log) | `503` | `MODELO_INDISPONIVEL` |
| Tempo total do atendimento acima de `[30 s]` | interrompe tentativas restantes | `503` | `TEMPO_ESGOTADO` |
| Erro não previsto | — | `500` | `ERRO_INTERNO` |

Regras:
- Nunca há resposta parcial nem resposta inventada no lugar de uma falha.
- `503` inclui `Retry-After` (padrão `[30]` segundos).
- O log registra o tipo da situação, a etapa, a duração e o `correlacaoId`, sem a pergunta completa nem o texto dos trechos.

---

## 7. Rastreabilidade com os requisitos

| Requisito | Contrato |
|---|---|
| RF01 | 3.2 |
| RF02, RF03 | 3.2 (efeitos: `quantidadeChunks`, `status`, erros 413/503) |
| RF04 | 3.5 e 4.3 |
| RF05 | 4 |
| RF06 | 5.1 |
| RF07 | 4.4, 6.1, 6.2 |
| RF08 | 6.3, 6.4 |
| RF09 | 5.1 (`FonteResponse`), 6.4 |
| RF10 | 2, 5.1, 6.5 |
| RNF01, RNF05 | 1.2, 1.3, 2 |
| RNF02 | 1.3 (`X-Correlation-Id`), 4.4 (`traceparent`), 5.2 |
| RNF04 | 6.5 |

## 8. Histórico de versões

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-10-22 | Versão inicial |
| 1.0.0 (revisão) | 2026-10-27 | Esclarecimentos da Fase 5, sem mudar campos, códigos nem a versão do servidor MCP (`1.0.0`): diferenças do schema publicado pelo `@McpTool` (§4.2), forma do texto de erro e erro de schema do SDK (§4.5). O limite de 10 requisições por minuto do diagnóstico (§3.5) continua para a Fase 8 |
| 1.0.0 (revisão) | 2026-10-27 | Esclarecimentos da Fase 6, sem mudar campos nem códigos: o componente `conhecimentoMcp` do health reporta `DEGRADADO` (§5.2); falhas de transporte do MCP também têm novas tentativas (§6.5); a resposta sem evidência é a mensagem seguida do canal humano (§5.1) |
