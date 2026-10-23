# Constituição do COTI AI Atendimento

Este documento reúne os princípios obrigatórios do projeto COTI AI Atendimento. Toda spec, plano, tarefa, código e revisão deve estar de acordo com ele. Quando outro documento contrariar esta constituição, vale a constituição até que ela seja emendada.

## Visão geral do sistema

A plataforma atende alunos da COTI Informática e é composta por dois microsserviços independentes:

| Serviço | Responsabilidade | Dados próprios |
|---|---|---|
| `api-conhecimento` | Mantém a base de conhecimento, faz o chunking, gera embeddings, armazena os vetores e expõe a pesquisa semântica como ferramenta MCP. | PostgreSQL com pgvector |
| `api-atendimentos` | Recebe perguntas por REST, consulta a `api-conhecimento` exclusivamente por MCP e gera a resposta com Spring AI e OpenAI. | Nenhum banco vetorial |

```
Aluno ──REST──▶ api-atendimentos ──MCP (Streamable HTTP)──▶ api-conhecimento ──▶ PostgreSQL + pgvector
                      │                                            │
                      └──────────── OpenAI (chat) ─────────────────┴──── OpenAI (embeddings)
```

## Como ler cada artigo

Cada artigo tem quatro partes:

- **Regra**: o que é obrigatório. Os verbos "deve" e "não deve" não admitem exceção sem emenda.
- **Justificativa**: por que a regra existe.
- **Consequência arquitetural**: o que a regra impõe ao desenho do sistema.
- **Como validar**: a evidência que prova o cumprimento (teste, configuração, checagem no CI ou revisão).

Um artigo sem evidência de validação é considerado não cumprido.

---

## Título I — Plataforma e stack

### Artigo 1. Java 21 e Spring Boot compatível com Spring AI 2.0.1

**Regra.** Os dois serviços devem usar Java 21 (LTS) e Spring AI 2.0.1. A versão do Spring Boot deve ser uma das suportadas pelo Spring AI 2.0.1 (linha Spring Boot 4.x), fixada de forma explícita no build. As versões das bibliotecas do Spring AI devem vir do BOM `spring-ai-bom` na versão 2.0.1, sem versões avulsas que o contrariem.

**Justificativa.** O Spring AI 2.0 foi construído sobre o Spring Boot 4 e o Spring Framework 7. Misturar versões fora da matriz de compatibilidade gera falhas de autoconfiguração difíceis de diagnosticar. Java 21 é LTS e traz records, pattern matching e virtual threads, úteis para chamadas de I/O ao modelo e ao MCP.

**Consequência arquitetural.**
- Os dois serviços compartilham a mesma linha de versões de Java, Spring Boot e Spring AI.
- A versão exata do Spring Boot (patch) é decidida em `specs/04-plan.md`, conferida contra as notas de lançamento do Spring AI 2.0.1, e registrada no build.
- Atualização de Spring AI ou Spring Boot é uma mudança própria, com execução completa dos testes e do eval (Artigo 15).

**Como validar.**
- O build define `java.version=21` (ou `toolchain` equivalente) e importa `spring-ai-bom:2.0.1`.
- O CI compila e testa com JDK 21.
- Nenhuma dependência `org.springframework.ai` declara versão própria fora do BOM (verificação na revisão do PR e por `dependency:tree`).

### Artigo 2. Spring AI e OpenAI

**Regra.** A integração com modelos deve usar Spring AI, com a OpenAI como provedor de chat e de embeddings. O identificador do modelo deve ser fixado com versão em configuração externa; alias que muda sozinho não vai para produção. Temperatura, limite de tokens e demais parâmetros ficam em configuração, com o motivo do valor documentado.

**Justificativa.** O Spring AI oferece abstrações (`ChatClient`, `EmbeddingModel`, `VectorStore`, MCP) que evitam acoplamento direto ao SDK do provedor. Fixar a versão do modelo evita que o comportamento mude sem aviso, o que invalidaria o eval.

**Consequência arquitetural.**
- O domínio não conhece Spring AI nem OpenAI. O modelo fica atrás de uma porta declarada na camada de aplicação, implementada por um adaptador na infraestrutura.
- Os prompts ficam em arquivos próprios, versionados no repositório (por exemplo, `src/main/resources/prompts/`), e não em strings no meio do código.
- Trocar de modelo ou de provedor exige rodar o eval e comparar com o resultado anterior.

**Como validar.**
- Teste de arquitetura (ArchUnit ou equivalente) garante que pacotes de domínio não importam `org.springframework.ai` nem `com.openai`.
- O identificador do modelo está em `application.yml` com valor vindo de propriedade ou variável de ambiente, e não em código Java.
- Os prompts existem como arquivos e são carregados por recurso.

### Artigo 3. Arquitetura modular e separação de responsabilidades

**Regra.** Cada serviço deve seguir Clean Architecture: a dependência aponta para dentro. O domínio não conhece framework, banco, HTTP nem MCP. A camada de aplicação orquestra os casos de uso e declara as portas. A infraestrutura implementa os adaptadores. Controllers e handlers MCP apenas traduzem o protocolo. Os dois serviços não compartilham banco, código de domínio nem biblioteca interna com regra de negócio.

**Justificativa.** Separar responsabilidades permite trocar provedor de modelo, banco vetorial ou transporte sem reescrever regra de negócio, e permite testar os casos de uso sem infraestrutura real.

**Consequência arquitetural.**
- Estrutura de pacotes por camada: `dominio`, `aplicacao` (casos de uso e portas), `infraestrutura` (adaptadores) e `api` (REST e MCP).
- Nomes de domínio em português (`PerguntaAluno`, `TrechoConhecimento`, `ResponderPerguntaService`) e termos técnicos em inglês (`Service`, `Port`, `Adapter`, `Controller`).
- Abstração só onde há fronteira real (OpenAI, PostgreSQL, MCP) ou uma segunda implementação.
- Cada serviço é implantado, versionado e escalado de forma independente.

**Como validar.**
- Teste de arquitetura verifica a direção das dependências entre camadas nos dois serviços.
- Os testes de casos de uso rodam sem Spring context, com dublês nas portas.
- Revisão de PR rejeita classe de domínio que dependa de framework.

---

## Título II — Integração e dados

### Artigo 4. Integração entre as APIs por MCP sobre HTTP

**Regra.** A `api-atendimentos` deve consultar a `api-conhecimento` exclusivamente por MCP, usando o transporte Streamable HTTP. É proibido acesso direto ao banco, chamada REST paralela ou cópia local da base de conhecimento. O endpoint MCP deve ser autenticado, ter timeout em toda chamada e não ficar exposto à internet pública.

**Justificativa.** Um único canal de integração mantém o contrato explícito e auditável. O MCP padroniza a descoberta e o schema das ferramentas. O Streamable HTTP é o transporte HTTP atual do MCP no Spring AI 2.0; o transporte SSE puro foi descartado porque está depreciado e marcado para remoção.

**Consequência arquitetural.**
- A `api-conhecimento` atua como servidor MCP e publica a ferramenta de pesquisa semântica com schema de entrada e de saída definido em `specs/06-api-contracts.md`.
- A `api-atendimentos` atua como cliente MCP. O cliente fica atrás de uma porta (`BaseConhecimentoPort` ou nome equivalente) para que o caso de uso não conheça o protocolo.
- A autenticação entre serviços usa credencial de serviço (por exemplo, token em variável de ambiente ou mTLS); a escolha fica em `specs/03-architecture.md`.
- Falha do servidor MCP (indisponível, timeout, resposta inválida) gera erro de domínio tratado na borda, com mensagem clara ao aluno (Artigo 11).
- Retry só para erro transitório, com backoff exponencial, variação aleatória e número máximo de tentativas.

**Como validar.**
- Teste de integração sobe a `api-conhecimento` (ou um servidor MCP de teste) e verifica que a `api-atendimentos` obtém trechos somente pela ferramenta MCP.
- Teste verifica que chamada sem credencial ao endpoint MCP é recusada.
- Teste verifica o comportamento com timeout e com servidor indisponível.
- A `api-atendimentos` não declara driver JDBC, `spring-ai-pgvector-store` nem dependência equivalente (verificação no build).

### Artigo 5. PostgreSQL com pgvector exclusivamente na API de Conhecimento

**Regra.** Somente a `api-conhecimento` deve ter acesso ao PostgreSQL com pgvector. Nenhum outro serviço pode receber suas credenciais de banco. O esquema é versionado por migração.

**Justificativa.** O banco vetorial é detalhe de implementação da base de conhecimento. Compartilhá-lo criaria acoplamento oculto e um segundo caminho de acesso aos dados, sem os controles do Artigo 9.

**Consequência arquitetural.**
- A tabela de chunks guarda, além do vetor e do texto: documento de origem, quem enviou, data de ingestão, status de publicação e modelo de embedding com versão.
- O filtro de autorização (por exemplo, só conteúdo publicado) é aplicado na própria busca vetorial, não depois dela.
- O esquema é criado por ferramenta de migração (Flyway ou Liquibase, decidida em `specs/04-plan.md`), não por criação automática em produção.
- Apagar um documento apaga seus chunks e vetores.

**Como validar.**
- Teste de integração com Testcontainers (imagem `pgvector/pgvector`) cobre ingestão, busca e remoção.
- As variáveis de conexão com o banco existem apenas na configuração da `api-conhecimento`.
- A migração inicial está no repositório e o teste roda sobre ela.

### Artigo 6. Geração de embeddings encapsulada na API de Conhecimento

**Regra.** Chunking e geração de embeddings, tanto na ingestão quanto na consulta, devem acontecer somente na `api-conhecimento`. A `api-atendimentos` envia texto pela ferramenta MCP e nunca envia nem recebe vetores. Vetores de modelos de embedding diferentes não se misturam no mesmo índice.

**Justificativa.** O vetor da consulta precisa ser gerado pelo mesmo modelo que gerou os vetores indexados. Concentrar a geração em um serviço garante essa consistência e permite trocar o modelo de embedding sem tocar no atendimento.

**Consequência arquitetural.**
- O `EmbeddingModel` e a estratégia de chunking ficam atrás de portas na `api-conhecimento`.
- O modelo de embedding e a dimensão do vetor ficam em configuração e são gravados em cada chunk.
- Trocar o modelo de embedding exige reindexar toda a base.
- Tamanho de chunk, sobreposição e `k` são decididos pelo eval de recuperação (Artigo 15), começando por valores simples.

**Como validar.**
- O contrato da ferramenta MCP em `specs/06-api-contracts.md` recebe texto e devolve trechos, sem campo de vetor.
- A `api-atendimentos` não declara dependência de `EmbeddingModel` (verificação no build e em teste de arquitetura).
- Teste verifica que a busca recusa ou ignora chunks com modelo de embedding diferente do configurado.

---

## Título III — Segurança e credenciais

### Artigo 7. Credenciais por variáveis de ambiente

**Regra.** Toda credencial (chave da OpenAI, usuário e senha do banco, credencial entre serviços) deve ser lida de variável de ambiente ou de cofre de segredos. Os arquivos de configuração referenciam a variável, nunca o valor.

**Justificativa.** Segredo em arquivo versionado vaza com o repositório, com o histórico e com cópias locais. Variável de ambiente permite rotacionar sem novo build.

**Consequência arquitetural.**
- `application.yml` usa placeholders como `${OPENAI_API_KEY}` e `${CONHECIMENTO_DB_PASSWORD}`.
- O repositório tem um `.env.example` com os nomes das variáveis e sem valores; `.env` está no `.gitignore`.
- A aplicação falha na inicialização, com mensagem clara, quando uma credencial obrigatória está ausente.
- Credenciais não aparecem em log, resposta de erro, endpoint do Actuator nem system prompt.

**Como validar.**
- `.gitignore` contém `.env`; `.env.example` existe sem valores.
- Teste de inicialização sem a variável obrigatória confirma a falha controlada.
- Os endpoints do Actuator expostos não incluem `env` nem `configprops` sem sanitização.

### Artigo 8. Nenhuma API key no código-fonte

**Regra.** Nenhuma chave de API, token, senha ou segredo pode existir no código-fonte, em teste, em arquivo de configuração, em Dockerfile, em pipeline ou no histórico do git. Testes usam valores falsos e dublês.

**Justificativa.** Uma chave da OpenAI vazada gera custo imediato e acesso indevido. Remover do histórico do git depois do vazamento é caro e nem sempre suficiente; a chave precisa ser revogada.

**Consequência arquitetural.**
- Os testes automatizados não chamam a OpenAI real; o modelo entra como dublê pela porta.
- O eval que chama o modelo real roda fora do CI comum, com chave injetada pelo ambiente.
- Um verificador de segredos roda antes do commit ou no CI.

**Como validar.**
- Verificador de segredos (por exemplo, gitleaks, a ser aprovado como dependência) no CI, bloqueando o merge quando encontra segredo.
- Busca no repositório por padrões de chave (`sk-`, `password=`, `apiKey:` com valor literal) sem ocorrências.
- O CI roda a suíte de testes sem nenhuma chave da OpenAI configurada.

### Artigo 9. Respostas baseadas exclusivamente em informações recuperadas e autorizadas

**Regra.** A resposta ao aluno deve se apoiar somente nos trechos devolvidos pela ferramenta MCP para aquela pergunta. Só podem ser recuperados trechos de documentos publicados e autorizados para consulta de alunos. A autorização é aplicada em código e na consulta ao banco, nunca apenas no texto do prompt. Toda resposta factual indica a fonte usada.

**Justificativa.** O aluno toma decisões (matrícula, pagamento, presença) com base na resposta. Resposta sem fonte é indistinguível de alucinação. Regra de segurança escrita só no prompt pode ser contornada pelo próprio modelo.

**Consequência arquitetural.**
- A ferramenta MCP filtra por status de publicação e visibilidade dentro da busca vetorial.
- A resposta da `api-atendimentos` tem estrutura definida (texto, fontes e indicação de conhecimento suficiente), validada contra schema antes de ser devolvida.
- O prompt separa com delimitadores claros a instrução, o contexto recuperado e a pergunta do aluno.
- O conhecimento geral do modelo não é fonte válida para informação da COTI.

**Como validar.**
- Teste de integração garante que documento não publicado nunca aparece no resultado da ferramenta MCP.
- Teste unitário garante que resposta sem fonte é rejeitada ou convertida em resposta de conhecimento insuficiente.
- Casos de eval verificam fidelidade ao trecho e presença da fonte correta.

### Artigo 10. Resistência a prompt injection contido nos documentos

**Regra.** Todo conteúdo da base de conhecimento e todo resultado de ferramenta é dado, nunca instrução. Texto dentro de um documento que tente mudar o comportamento do assistente (ignorar regras, revelar o prompt, chamar ferramentas, mudar o formato da resposta) não deve ser obedecido. A defesa principal é estrutural: o modelo tem poucos privilégios.

**Justificativa.** Documentos são escritos por pessoas e podem ser alterados de forma maliciosa ou acidental. Um classificador de injeção é uma camada útil, mas falha; o que segura o dano é o modelo não ter o que fazer de perigoso com a injeção.

**Consequência arquitetural.**
- O modelo da `api-atendimentos` tem acesso somente à ferramenta de pesquisa, que é de leitura. Nenhuma ferramenta com efeito externo (enviar e-mail, matricular, alterar dado) é exposta a ele.
- O assistente não reúne acesso a dado privado, leitura de conteúdo não confiável e canal de saída para terceiros.
- Os trechos recuperados entram no prompt dentro de delimitadores próprios, com instrução explícita de que são dados.
- A ingestão segue uma esteira: valida tipo pelo conteúdo e tamanho, extrai texto, analisa o texto em busca de padrões de injeção e só então indexa. O endpoint de ingestão é autenticado, autorizado e limitado.
- A saída do modelo é tratada como entrada não confiável e validada contra schema; não vai direto para SQL, HTML ou ferramenta.

**Como validar.**
- Conjunto de eval com casos adversariais: documento com instrução embutida, pedido para revelar o system prompt, pedido de dado de outro aluno. Nota mínima definida em `specs/07-test-plan.md`.
- Teste verifica que o cliente MCP da `api-atendimentos` expõe ao modelo somente a ferramenta de pesquisa.
- Teste da esteira de ingestão cobre arquivo com tipo falso e arquivo acima do limite.

### Artigo 11. Tratamento explícito quando não houver conhecimento suficiente

**Regra.** Quando a busca não devolver trechos relevantes, ou quando os trechos não responderem à pergunta, o sistema deve dizer de forma explícita que não tem a informação e orientar o aluno a um canal humano de atendimento. Falha técnica (MCP fora do ar, OpenAI indisponível, limite atingido) também gera mensagem clara, nunca erro genérico nem resposta inventada.

**Justificativa.** Uma recusa honesta preserva a confiança do aluno; uma resposta inventada pode causar prejuízo real. Distinguir "não sei" de "estou com problema técnico" ajuda o aluno e a operação.

**Consequência arquitetural.**
- A decisão de conhecimento insuficiente tem uma parte determinística em código (nenhum trecho acima do limiar de similaridade configurado) antes de chamar o modelo, o que evita custo e alucinação.
- A resposta estruturada tem um campo que indica se houve conhecimento suficiente.
- O texto de recusa e o canal humano de encaminhamento vêm de configuração, não do modelo.
- Erros de domínio com nome claro (`ConhecimentoInsuficienteException`, `BaseConhecimentoIndisponivelException` ou equivalentes) são tratados na borda e mapeados para respostas definidas no contrato.

**Como validar.**
- Teste unitário: busca vazia não chama o modelo e devolve a resposta de conhecimento insuficiente.
- Teste de integração: MCP indisponível e OpenAI indisponível geram as respostas definidas em `specs/06-api-contracts.md`.
- Casos de eval com perguntas fora da base exigem recusa.

### Artigo 12. Proibição de inventar cursos, preços, datas e horários

**Regra.** O sistema não deve afirmar nome de curso, preço, condição de pagamento, data, horário, carga horária, vaga ou professor que não esteja literalmente presente nos trechos recuperados para aquela pergunta. Na ausência da informação, aplica-se o Artigo 11.

**Justificativa.** Esses dados geram compromisso comercial e expectativas do aluno. Um preço ou horário inventado pode ser cobrado da COTI como oferta, além de prejudicar o aluno.

**Consequência arquitetural.**
- O prompt proíbe explicitamente esses dados sem fonte, mas a regra não depende só do prompt.
- Uma validação em código na saída verifica que valores monetários, datas e horários presentes na resposta também aparecem nos trechos recuperados. Resposta reprovada volta ao modelo com o erro, no máximo duas vezes, e depois cai na resposta de conhecimento insuficiente.
- Informação com prazo de validade (turmas, preços) guarda data de vigência na origem do documento, para que conteúdo vencido possa ser despublicado.

**Como validar.**
- Teste unitário do validador de saída com respostas que contêm preço, data ou horário ausentes dos trechos.
- Casos de eval específicos para pedidos de preço, data e horário inexistentes na base, com nota mínima de 100% de recusa correta.

---

## Título IV — Qualidade e operação

### Artigo 13. Logging, observabilidade e rastreabilidade

**Regra.** Cada pergunta deve ter um identificador de correlação que ligue, em um único trace, a requisição REST, a chamada MCP, os trechos recuperados (por identificador), a chamada ao modelo e a resposta. Os logs devem ser estruturados e não devem conter credencial nem dado pessoal em claro.

**Justificativa.** Sem rastreabilidade não é possível explicar por que o assistente deu uma resposta, investigar uma reclamação de aluno, medir custo nem transformar falha de produção em caso de eval.

**Consequência arquitetural.**
- Uso do Micrometer Observation e do tracing do Spring Boot, com propagação do contexto de trace na chamada MCP entre os serviços.
- Métricas por funcionalidade e por versão de prompt e de modelo: latência, tokens de entrada e de saída, custo estimado, taxa de erro, taxa de saída inválida e taxa de conhecimento insuficiente.
- Log registra metadados sempre (ids, versões, tempos, contagem de trechos). Texto da pergunta e da resposta só mascarado, com prazo de retenção definido em `specs/02-requirements.md`.
- O registro de prompt e completion do Spring AI fica desligado por padrão em produção.

**Como validar.**
- Teste de integração verifica que o mesmo trace id aparece nas duas APIs para uma pergunta.
- Teste do mascaramento de log com e-mail, CPF e telefone na pergunta.
- As métricas exigidas aparecem no endpoint de métricas do Actuator.

### Artigo 14. Proteção de dados pessoais (LGPD)

**Regra.** Dado pessoal do aluno segue a LGPD desde o desenho: finalidade definida, mínimo necessário, prazo de retenção e forma de exclusão. A pergunta do aluno deve ser enviada à OpenAI somente com dados pessoais identificáveis mascarados ou removidos, e somente depois de confirmada a existência de contrato que coloque o provedor como operador, sem uso dos dados para treino, com retenção definida e base para transferência internacional (art. 33).

**Justificativa.** Alunos podem escrever CPF, telefone, e-mail ou situação financeira na pergunta. A OpenAI processa fora do Brasil; dado pseudonimizado continua sendo dado pessoal.

**Consequência arquitetural.**
- Um componente de mascaramento atua na `api-atendimentos` antes de qualquer envio ao modelo e antes de qualquer log.
- A base de conhecimento não contém dados pessoais de alunos; a esteira de ingestão alerta quando os encontra.
- A conversa não é armazenada além do prazo definido na spec de requisitos.

**Como validar.**
- Teste unitário do mascaramento antes da porta do modelo.
- Item no `specs/08-checklist.md` confirmando o contrato com o provedor antes da ida para produção.

### Artigo 15. Testes automatizados e validação por critérios de aceite

**Regra.** Todo comportamento novo ou alterado entra com teste automatizado. Cada requisito tem critérios de aceite verificáveis em `specs/02-requirements.md`, e cada critério é coberto por teste ou por caso de eval. O comportamento do modelo é coberto por eval versionado no repositório, com nota mínima.

**Justificativa.** Teste cobre o código em volta do modelo; eval cobre o modelo. Sem medição, não é possível afirmar que um prompt, um modelo ou um ajuste de chunking ficou melhor ou pior.

**Consequência arquitetural.**
- Testes unitários dos casos de uso com dublês nas portas; testes de integração com Testcontainers para PostgreSQL com pgvector e com servidor MCP real ou de teste.
- O CI não chama modelo real.
- O eval mede separadamente a recuperação (o trecho certo veio entre os `k` primeiros?) e a resposta (fiel ao trecho, cita a fonte, recusa quando não há fonte).
- O eval roda a cada mudança de prompt, modelo, parâmetro, chunking, embedding ou ferramenta. Queda abaixo da nota mínima bloqueia a mudança.
- Toda falha de IA corrigida vira caso novo no eval.
- Toda chamada ao modelo tem limite em código: tokens de saída, timeout, número de chamadas de ferramenta por pergunta e limite de requisições por usuário.

**Como validar.**
- O CI executa a suíte e bloqueia o merge quando falha.
- Matriz de rastreabilidade em `specs/07-test-plan.md` liga cada critério de aceite ao teste ou caso de eval correspondente.
- Relatório do eval anexado ao PR que muda prompt, modelo ou parâmetro.

### Artigo 16. Documentação OpenAPI

**Regra.** Todo endpoint REST dos dois serviços deve ser documentado em OpenAPI 3, com schemas de requisição, resposta e erro, exemplos e esquema de segurança. A documentação deve estar de acordo com `specs/06-api-contracts.md`.

**Justificativa.** O contrato documentado permite integração por terceiros (front-end, canais de atendimento) sem leitura do código e serve de base para teste de contrato.

**Consequência arquitetural.**
- Geração da especificação a partir do código (por exemplo, springdoc-openapi, a ser aprovado como dependência e conferido quanto à compatibilidade com Spring Boot 4).
- A interface interativa (Swagger UI) fica desabilitada ou protegida em produção.
- As respostas de erro seguem um formato único (por exemplo, Problem Details, RFC 9457), sem stack trace, nome de classe ou detalhe interno.
- A ferramenta MCP, que não é REST, é documentada pelo próprio schema MCP e descrita em `specs/06-api-contracts.md`.

**Como validar.**
- Teste que obtém a especificação OpenAPI gerada e a compara com o contrato esperado.
- Teste que verifica que respostas de erro não contêm stack trace nem nome de classe.

### Artigo 17. Respeito aos contratos definidos em specs/06-api-contracts.md

**Regra.** Os endpoints REST, a ferramenta MCP, seus schemas, códigos de status e mensagens de erro devem seguir exatamente o definido em `specs/06-api-contracts.md`. Mudança de contrato é feita primeiro na spec, por PR, e só depois no código. Mudança incompatível exige nova versão do contrato.

**Justificativa.** Os dois serviços evoluem de forma independente; o contrato é o que permite isso sem quebrar o outro lado. Código que diverge da spec torna a spec inútil.

**Consequência arquitetural.**
- DTOs de entrada e saída ficam na camada de API e são mapeados para o domínio; o domínio não é exposto diretamente no contrato.
- Validação de entrada no backend (Bean Validation), com limites de tamanho para a pergunta e para os parâmetros da ferramenta MCP.
- Versionamento explícito do contrato REST (por exemplo, `/api/v1`) e do nome ou versão da ferramenta MCP.

**Como validar.**
- Testes de contrato para cada endpoint REST e para a ferramenta MCP, derivados de `specs/06-api-contracts.md`.
- Revisão de PR rejeita mudança de contrato sem alteração correspondente na spec.

---

## Título V — Emendas

### Artigo 18. Alteração desta constituição

**Regra.** Esta constituição só muda por PR que altere este arquivo, com justificativa da mudança e indicação dos artigos afetados. Specs, planos e tarefas que dependem do artigo alterado devem ser revistos no mesmo PR ou em PR imediatamente seguinte.

**Justificativa.** Princípios que mudam sem registro deixam de ser princípios. A revisão conjunta evita que a spec e o código fiquem em desacordo com a regra nova.

**Consequência arquitetural.** Exceção pontual a um artigo não é permitida por comentário em código ou decisão verbal; ou se emenda a constituição, ou se cumpre a regra.

**Como validar.** O histórico do git deste arquivo mostra cada alteração com mensagem no padrão Conventional Commits e referência aos artigos alterados.

---

## Pontos em aberto

Itens que esta constituição exige mas que dependem de decisão em outra spec:

| Item | Onde decidir |
|---|---|
| Versão exata (patch) do Spring Boot 4.x compatível com Spring AI 2.0.1 | `specs/04-plan.md` |
| Modelos da OpenAI (chat e embedding) com versão fixada e justificativa | `specs/03-architecture.md` |
| Mecanismo de autenticação entre serviços no MCP | `specs/03-architecture.md` |
| Limiar de similaridade, tamanho de chunk e `k` iniciais | `specs/04-plan.md`, ajustados pelo eval |
| Notas mínimas do eval por categoria | `specs/07-test-plan.md` |
| Prazos de retenção de log e de conversa | `specs/02-requirements.md` |
| Contrato com a OpenAI como operadora de dados (LGPD) | `specs/08-checklist.md` |
| Ferramentas de migração, OpenAPI e verificação de segredos (dependências a aprovar) | `specs/04-plan.md` |
