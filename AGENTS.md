# AGENTS.md — Instruções operacionais para agentes de codificação

Este arquivo vale para todo agente de codificação (e para toda pessoa) que trabalhar no COTI AI Atendimento. Ele diz **como** trabalhar. As regras de **o que** o sistema deve ser estão em [`specs/01-constitution.md`](specs/01-constitution.md), que tem precedência sobre este arquivo em caso de conflito.

## Projeto em uma frase

Dois microsserviços Java independentes: a `api-conhecimento` mantém a base de conhecimento (chunking, embeddings, PostgreSQL com pgvector) e expõe a pesquisa semântica como ferramenta MCP; a `api-atendimentos` recebe perguntas por REST, consulta a base **somente por MCP** e gera a resposta com Spring AI e OpenAI.

---

## 1. Leitura obrigatória antes de implementar

Antes de implementar qualquer funcionalidade, leia:

| Arquivo | Para quê |
|---|---|
| [`specs/01-constitution.md`](specs/01-constitution.md) | Princípios obrigatórios. Nenhuma tarefa pode violá-los. |
| [`specs/02-requirements.md`](specs/02-requirements.md) | Requisitos e critérios de aceite da funcionalidade. |
| [`specs/03-architecture.md`](specs/03-architecture.md) | Camadas, portas, adaptadores, modelos e decisões técnicas. |
| [`specs/06-api-contracts.md`](specs/06-api-contracts.md) | Contratos REST e da ferramenta MCP. |

Consulte também, conforme a etapa: [`specs/04-plan.md`](specs/04-plan.md) (versões e plano), [`specs/05-tasks.md`](specs/05-tasks.md) (tarefa atual), [`specs/07-test-plan.md`](specs/07-test-plan.md) (testes e eval) e [`specs/08-checklist.md`](specs/08-checklist.md) (checagem final).

Se uma spec obrigatória estiver vazia, incompleta ou contraditória para a tarefa, **pare e informe** o que falta. Não preencha a lacuna por conta própria com uma decisão de arquitetura ou de contrato.

## 2. Skills

Consulte a skill da área antes de começar a tarefa. Mais de uma pode se aplicar.

| Skill | Quando usar |
|---|---|
| [`skills/spring-ai/SKILL.md`](skills/spring-ai/SKILL.md) | `ChatClient`, `EmbeddingModel`, prompts, configuração da OpenAI, saída estruturada. |
| [`skills/mcp-integration/SKILL.md`](skills/mcp-integration/SKILL.md) | Servidor MCP na `api-conhecimento`, cliente MCP na `api-atendimentos`, transporte Streamable HTTP. |
| [`skills/rag-pgvector/SKILL.md`](skills/rag-pgvector/SKILL.md) | Ingestão, chunking, `VectorStore`, esquema e busca no pgvector. |
| [`skills/testing/SKILL.md`](skills/testing/SKILL.md) | Testes unitários, de integração, de contrato, Testcontainers e eval. |

Uma skill não substitui a documentação oficial atual. Nome de classe, propriedade ou método do Spring AI, do MCP ou do Spring Boot que você não conferiu deve ser dito como não conferido.

## 3. Escopo

- Implemente **somente** o que a tarefa atual pede.
- Melhoria, refatoração ou bug fora do escopo: aponte em **Pendências** no relatório final e deixe para decisão humana.
- Não adicione dependência nova sem aprovação. Ao propor uma, informe para que serve, se é mantida e qual a licença.

---

## 4. Regras técnicas

### 4.1 Stack e versões

- Java 21, Spring Boot e Spring AI **nas versões definidas em `specs/03-architecture.md` e `specs/04-plan.md`** (Spring AI 2.0.1, linha Spring Boot 4.x, conforme o Artigo 1 da constituição).
- Versões do Spring AI vêm do `spring-ai-bom`. Não declare versão avulsa de artefato `org.springframework.ai`.
- Não troque versão de Java, Spring Boot, Spring AI ou modelo de IA como efeito colateral de outra tarefa.

### 4.2 Fronteiras entre os serviços

- A `api-atendimentos` **não** depende de PostgreSQL, pgvector, JDBC, JPA, driver de banco, `VectorStore` nem `EmbeddingModel`. Se a tarefa parecer exigir isso, ela está no serviço errado.
- **Toda** busca de conhecimento feita pela `api-atendimentos` passa pela ferramenta MCP exposta pela `api-conhecimento`. Não crie chamada REST paralela, acesso direto ao banco, cache local da base nem conhecimento embutido no prompt.
- Chunking e embeddings acontecem só na `api-conhecimento`.
- O modelo da `api-atendimentos` só recebe ferramentas de leitura. Nenhuma ferramenta com efeito externo é exposta a ele.

### 4.3 Código

- O código entregue **compila**, é organizado por camadas (`dominio`, `aplicacao`, `infraestrutura`, `api`) e é testável sem infraestrutura real.
- O domínio não importa Spring, Spring AI, JDBC nem MCP.
- **DTOs são `record`**. Use `record` também para objetos de valor imutáveis.
- **Injeção de dependência por construtor**, com campos `final`. Não use `@Autowired` em campo nem em setter.
- Nomes de domínio em português (`PerguntaAluno`, `ResponderPerguntaService`); termos técnicos em inglês (`Controller`, `Port`, `Adapter`).
- Não engula exceção. Use exceção de domínio com nome claro e trate na borda.
- Prompts ficam em arquivos versionados, não em strings no código. Modelo, temperatura e limites ficam em configuração.
- Toda chamada ao modelo e ao MCP tem timeout e limite de tokens.

### 4.4 Segredos e logs

- Credenciais vêm de variáveis de ambiente. Nunca escreva chave, senha ou token em código, teste, `application.yml`, Dockerfile, `docker-compose.yml` ou mensagem de commit. Use `.env` (fora do git) e mantenha `.env.example` sem valores.
- **Por padrão, os logs não registram**:
  - API keys, tokens, senhas;
  - dados pessoais ou sensíveis (CPF, e-mail, telefone, dados financeiros);
  - o texto completo da pergunta do aluno nem da resposta do modelo.
- Registre metadados: id de correlação, versões de prompt e de modelo, tempos, contagem de trechos, tokens. Texto só mascarado e quando a spec permitir.
- O log de prompt e completion do Spring AI fica desligado por padrão.

### 4.5 Contratos

- Contratos aprovados em `specs/06-api-contracts.md` são a fonte da verdade.
- Para alterar um contrato: informe o motivo, atualize **primeiro** a spec, depois o código e os testes de contrato. Mudança incompatível exige nova versão.
- Nunca altere contrato em silêncio para fazer um teste passar.

### 4.6 Docker

- Use o **Docker (via Docker Desktop)** para construir as imagens dos serviços e subir os serviços de apoio, como o PostgreSQL com pgvector.
- A orquestração local fica em `docker-compose.yml` na raiz (quando a tarefa correspondente criá-lo). Credenciais entram por `.env`, nunca no arquivo compose.
- Use imagem oficial com versão fixada (por exemplo, `pgvector/pgvector` com tag explícita). Não use `latest`.
- Os testes de integração usam Testcontainers, que também depende do Docker Desktop em execução.
- Antes de rodar build de container ou testes de integração, confirme que o Docker está ativo (`docker info`). Se não estiver, informe em vez de pular os testes em silêncio.

---

## 5. Testes e verificação

- Todo comportamento criado ou alterado entra com teste novo ou atualizado.
- Testes unitários usam dublês nas portas. O CI e os testes automatizados **não** chamam a OpenAI real.
- Testes de integração usam Testcontainers (`pgvector/pgvector`) e servidor MCP real ou de teste.
- Mudança em prompt, modelo, parâmetro, chunking, embedding ou ferramenta exige rodar o eval definido em `specs/07-test-plan.md` e comparar com o resultado anterior.
- **Antes de concluir uma tarefa**, execute a compilação e os testes relevantes do serviço afetado, por exemplo:

```bash
./mvnw -pl api-atendimentos verify
```

  (Ajuste ao build definido em `specs/04-plan.md`.)

- **Nunca afirme que um teste passou sem tê-lo executado.** Se não rodou, diga que não rodou e por quê. Se falhou, mostre a saída relevante.

---

## 6. Fluxo operacional: Spec → Plan → Implement → Test → Review

Toda funcionalidade segue as cinco etapas, nesta ordem. Correção pequena e isolada pode começar em **Implement**, mas ainda passa por **Test** e **Review**.

### 6.1 Spec

- Leia as specs da Seção 1 e identifique os critérios de aceite da tarefa.
- Confira se a tarefa respeita a constituição e os contratos.
- Saída: entendimento confirmado ou lista de lacunas. Com lacuna que bloqueie, **pare e pergunte**.

### 6.2 Plan

- Liste os arquivos que serão criados ou alterados, as portas e adaptadores envolvidos, os testes que provarão cada critério de aceite e as skills consultadas.
- Indique dependências novas (que exigem aprovação) e riscos de segurança ou LGPD.
- Saída: plano curto, aprovado antes de mudanças grandes.

### 6.3 Implement

- Implemente só o planejado, seguindo a Seção 4.
- Comece pelo domínio e pelos casos de uso; adaptadores e controllers por último.
- Saída: código compilável.

### 6.4 Test

- Escreva ou atualize os testes; rode a compilação e os testes relevantes; rode o eval quando aplicável.
- Saída: comandos executados e resultado real (aprovado, falhou com saída, ou não executado com motivo).

### 6.5 Review

Antes de declarar a tarefa concluída, confira:

- [ ] Critérios de aceite atendidos e cobertos por teste ou eval.
- [ ] Nenhuma violação da constituição (em especial Artigos 4, 5, 6, 9, 10, 11 e 12).
- [ ] `api-atendimentos` sem dependência de banco ou pgvector; busca só por MCP.
- [ ] Contratos inalterados, ou alterados com motivo e spec atualizada.
- [ ] Nenhum segredo em código ou configuração; logs sem dado sensível nem pergunta completa.
- [ ] DTOs como `record`; injeção por construtor.
- [ ] Nada fora do escopo foi implementado.

---

## 7. Relatório ao final de cada interação

Termine toda interação com este relatório, mesmo que a tarefa não tenha sido concluída:

```markdown
## Relatório

**Arquivos criados**
- caminho/arquivo — finalidade

**Arquivos modificados**
- caminho/arquivo — o que mudou

**Comandos executados**
- `comando` — resultado

**Testes realizados**
- O que foi executado e o resultado real (passou / falhou / não executado e motivo)

**Pendências**
- O que ficou faltando, lacunas de spec, riscos e itens fora do escopo identificados

**Próxima tarefa recomendada**
- Uma tarefa, referenciando specs/05-tasks.md quando possível
```

Use "nenhum" quando uma seção estiver vazia. Não omita seções.
