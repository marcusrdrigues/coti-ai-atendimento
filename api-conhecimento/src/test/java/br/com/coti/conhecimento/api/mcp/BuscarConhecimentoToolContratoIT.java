package br.com.coti.conhecimento.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import br.com.coti.conhecimento.suporte.ChavesTeste;
import br.com.coti.conhecimento.suporte.ClienteMcpDeTeste;
import br.com.coti.conhecimento.suporte.EmbeddingModelFalso;
import br.com.coti.conhecimento.suporte.TesteServidor;
import br.com.coti.conhecimento.suporte.TrechosDeTeste;
import br.com.coti.conhecimento.suporte.TrechosDeTeste.Trecho;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato da ferramenta {@code buscar_conhecimento} (specs/06-api-contracts.md §4) com um cliente MCP real, por
 * Streamable HTTP, contra o servidor em porta aleatória e PostgreSQL real. Os testes de "divergência" travam o que
 * o {@code @McpTool} do Spring AI 2.0.1 gera de diferente do contrato, para que uma atualização da biblioteca que
 * mude isso apareça aqui.
 */
@TesteServidor
@TestPropertySource(properties = "coti.conhecimento.busca.tempo-limite=1s")
class BuscarConhecimentoToolContratoIT {

    private static final String FERRAMENTA = "buscar_conhecimento";
    private static final String PERGUNTA = "As aulas do curso de Angular são online?";

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EmbeddingModelFalso embeddings;

    @MockitoSpyBean
    private VectorStore vectorStore;

    private final JsonMapper json = JsonMapper.builder().build();
    private McpSyncClient cliente;

    @BeforeEach
    void iniciar() {
        TrechosDeTeste.zerar();
        embeddings.reiniciar();
        cliente = ClienteMcpDeTeste.criar(porta, ChavesTeste.MCP);
    }

    @AfterEach
    void encerrar() {
        cliente.close();
    }

    private CallToolResult chamar(Map<String, Object> argumentos) {
        return cliente.callTool(new CallToolRequest(FERRAMENTA, argumentos));
    }

    private String texto(CallToolResult resultado) {
        return ((TextContent) resultado.content().get(0)).text();
    }

    private JsonNode erro(CallToolResult resultado) {
        assertThat(resultado.isError()).isTrue();
        assertThat(resultado.content()).hasSize(1);
        assertThat(texto(resultado).strip()).as("o texto do erro e um unico JSON").startsWith("{").endsWith("}");
        return json.readTree(texto(resultado));
    }

    private Tool ferramenta() {
        List<Tool> ferramentas = cliente.listTools().tools();
        assertThat(ferramentas).extracting(Tool::name).containsExactly(FERRAMENTA);
        return ferramentas.get(0);
    }

    private static List<String> textos(Object valor) {
        return ((List<?>) valor).stream().map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object valor) {
        return (Map<String, Object>) valor;
    }

    // ---- servidor e tools/list (§4.1 e §4.2)

    @Test
    void servidorSeApresentaComNomeEVersaoDoContratoEPublicaSoFerramentas() {
        assertThat(cliente.getServerInfo().name()).isEqualTo("coti-conhecimento");
        assertThat(cliente.getServerInfo().version()).isEqualTo("1.0.0");
        assertThat(cliente.getServerCapabilities().tools()).isNotNull();
        assertThat(cliente.getServerCapabilities().resources()).isNull();
        assertThat(cliente.getServerCapabilities().prompts()).isNull();
    }

    @Test
    void publicaApenasABuscaComNomeTituloEDescricaoDoContrato() {
        Tool tool = ferramenta();

        assertThat(tool.name()).isEqualTo("buscar_conhecimento");
        assertThat(tool.title()).isEqualTo("Buscar conhecimento COTI");
        assertThat(tool.description()).isEqualTo("Busca trechos publicados da base de conhecimento oficial da COTI "
                + "Informática semanticamente relacionados à pergunta.");
    }

    @Test
    void asAnotacoesDeSomenteLeituraEstaoComoNoContrato() {
        var anotacoes = ferramenta().annotations();

        assertThat(anotacoes.readOnlyHint()).isTrue();
        assertThat(anotacoes.destructiveHint()).isFalse();
        assertThat(anotacoes.idempotentHint()).isTrue();
        assertThat(anotacoes.openWorldHint()).isFalse();
    }

    @Test
    void inputSchemaTemPerguntaObrigatoriaELimiteOpcional() {
        Map<String, Object> schema = ferramenta().inputSchema();
        Map<String, Object> propriedades = mapa(schema.get("properties"));

        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(propriedades).containsOnlyKeys("pergunta", "limite");
        assertThat(mapa(propriedades.get("pergunta")).get("type")).isEqualTo("string");
        assertThat(mapa(propriedades.get("limite")).get("type")).isEqualTo("integer");
        assertThat(mapa(propriedades.get("pergunta")).get("description"))
                .isEqualTo("Pergunta ou texto a pesquisar na base de conhecimento.");
        assertThat(mapa(propriedades.get("limite")).get("description"))
                .isEqualTo("Quantidade máxima de trechos a devolver.");
        assertThat(textos(schema.get("required"))).containsExactly("pergunta");
    }

    @Test
    void divergenciaConhecida_inputSchemaGeradoNaoTemLimitesDeTamanhoNemPadrao() {
        // O contrato (§4.2) pede minLength/maxLength/minimum/maximum/default/additionalProperties:false. O
        // @McpToolParam só aceita descrição e obrigatoriedade. As regras valem no servidor (BuscarConhecimentoService).
        Map<String, Object> schema = ferramenta().inputSchema();
        Map<String, Object> pergunta = mapa(mapa(schema.get("properties")).get("pergunta"));
        Map<String, Object> limite = mapa(mapa(schema.get("properties")).get("limite"));

        assertThat(pergunta).doesNotContainKeys("minLength", "maxLength");
        assertThat(limite).doesNotContainKeys("minimum", "maximum", "default");
        assertThat(schema).doesNotContainKey("additionalProperties");
    }

    @Test
    void outputSchemaDescreveTrechosETotalComOsCamposDoContrato() {
        Map<String, Object> schema = ferramenta().outputSchema();
        Map<String, Object> propriedades = mapa(schema.get("properties"));
        Map<String, Object> trechos = mapa(propriedades.get("trechos"));
        Map<String, Object> item = mapa(trechos.get("items"));
        Map<String, Object> camposDoTrecho = mapa(item.get("properties"));

        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(propriedades).containsOnlyKeys("trechos", "total");
        assertThat(textos(schema.get("required"))).containsExactlyInAnyOrder("trechos", "total");
        assertThat(mapa(propriedades.get("total")).get("type")).isEqualTo("integer");
        assertThat(trechos.get("type")).isEqualTo("array");
        assertThat(camposDoTrecho).containsOnlyKeys("id", "texto", "documentoId", "titulo", "fonte", "categoria",
                "similaridade");
        assertThat(mapa(camposDoTrecho.get("id")).get("format")).isEqualTo("uuid");
        assertThat(mapa(camposDoTrecho.get("documentoId")).get("format")).isEqualTo("uuid");
        assertThat(textos(mapa(camposDoTrecho.get("categoria")).get("enum"))).containsExactly("CURSO", "PROGRAMA",
                "TECNOLOGIA", "MODALIDADE", "PRE_REQUISITO", "INSTITUCIONAL");
        assertThat(mapa(camposDoTrecho.get("similaridade")).get("type")).isEqualTo("number");
        assertThat(textos(item.get("required")))
                .containsExactlyInAnyOrder("id", "texto", "documentoId", "titulo", "fonte", "categoria");
    }

    @Test
    void outputSchemaNaoExpoeCamposInternos() {
        Map<String, Object> item = mapa(mapa(mapa(ferramenta().outputSchema().get("properties")).get("trechos"))
                .get("items"));

        assertThat(mapa(item.get("properties"))).doesNotContainKeys("status", "modeloEmbedding", "vigenteAte", "ordem",
                "embedding", "metadata", "cadastradoEm");
    }

    @Test
    void divergenciaConhecida_outputSchemaGeradoNaoTemMaxItemsNemMaxLengthENaoAceitaSimilaridadeNula() {
        // O contrato (§4.2) pede maxItems 10, maxLength 2000, minimum/maximum 0..1 e similaridade ["number","null"].
        // O gerador do Spring AI produz só tipo e formato. Os limites valem no caso de uso; a similaridade nunca é
        // nula com o pgvector (o score sempre existe).
        Map<String, Object> trechos = mapa(mapa(ferramenta().outputSchema().get("properties")).get("trechos"));
        Map<String, Object> campos = mapa(mapa(trechos.get("items")).get("properties"));

        assertThat(trechos).doesNotContainKey("maxItems");
        assertThat(mapa(campos.get("texto"))).doesNotContainKey("maxLength");
        assertThat(mapa(campos.get("similaridade"))).doesNotContainKeys("minimum", "maximum");
        assertThat(mapa(campos.get("similaridade")).get("type")).isEqualTo("number");
    }

    // ---- tools/call com sucesso (§4.3 e §4.4)

    @Test
    void chamadaValidaDevolveStructuredContentComOsCamposDoContrato() {
        Trecho gravado = Trecho.publicado("O curso de Angular é oferecido nas modalidades presencial e online ao vivo.",
                0.88).comCategoria("MODALIDADE");
        TrechosDeTeste.gravar(jdbc, PERGUNTA, gravado);

        CallToolResult resultado = chamar(Map.of("pergunta", PERGUNTA, "limite", 5));

        assertThat(resultado.isError()).isFalse();
        JsonNode estruturado = json.valueToTree(resultado.structuredContent());
        assertThat(estruturado.get("total").asInt()).isEqualTo(1);
        JsonNode trecho = estruturado.get("trechos").get(0);
        assertThat(trecho.get("id").asString()).isEqualTo(gravado.id().toString());
        assertThat(trecho.get("texto").asString()).isEqualTo(gravado.texto());
        assertThat(trecho.get("documentoId").asString()).isEqualTo(gravado.documentoId().toString());
        assertThat(trecho.get("titulo").asString()).isEqualTo("Título");
        assertThat(trecho.get("fonte").asString()).isEqualTo("Fonte");
        assertThat(trecho.get("categoria").asString()).isEqualTo("MODALIDADE");
        assertThat(trecho.get("similaridade").asDouble()).isBetween(0.87, 0.89);
    }

    @Test
    void oConteudoEmTextoEEquivalenteAoStructuredContent() {
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("Trecho A", 0.9));
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("Trecho B", 0.8));

        CallToolResult resultado = chamar(Map.of("pergunta", PERGUNTA));

        assertThat(resultado.content()).hasSize(1);
        assertThat(json.readTree(texto(resultado))).isEqualTo(json.valueToTree(resultado.structuredContent()));
    }

    @Test
    void semLimiteDevolveNoMaximoCincoTrechos() {
        for (int i = 0; i < 8; i++) {
            TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("c" + i, 0.99 - i * 0.02));
        }

        JsonNode estruturado = json.valueToTree(chamar(Map.of("pergunta", PERGUNTA)).structuredContent());

        assertThat(estruturado.get("trechos")).hasSize(5);
        assertThat(estruturado.get("total").asInt()).isEqualTo(5);
    }

    @Test
    void limiteInformadoEhRespeitadoEOrdemEPorSimilaridadeDecrescente() {
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("fraco", 0.75));
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("forte", 0.95));
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("medio", 0.85));

        JsonNode dois = json.valueToTree(chamar(Map.of("pergunta", PERGUNTA, "limite", 2)).structuredContent());

        assertThat(dois.get("trechos")).hasSize(2);
        assertThat(dois.get("trechos").get(0).get("texto").asString()).isEqualTo("forte");
        assertThat(dois.get("trechos").get(1).get("texto").asString()).isEqualTo("medio");
    }

    @Test
    void semTrechoRelevanteEhSucessoComListaVazia() {
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("distante", 0.30));

        CallToolResult resultado = chamar(Map.of("pergunta", PERGUNTA));

        assertThat(resultado.isError()).isFalse();
        JsonNode estruturado = json.valueToTree(resultado.structuredContent());
        assertThat(estruturado.get("trechos")).isEmpty();
        assertThat(estruturado.get("total").asInt()).isZero();
    }

    @Test
    void somaDosTextosNaoPassaDe8MilENenhumTextoPassaDe2Mil() {
        for (int i = 0; i < 5; i++) {
            TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("x".repeat(2_400), 0.95 - i * 0.02));
        }

        JsonNode estruturado = json.valueToTree(chamar(Map.of("pergunta", PERGUNTA, "limite", 10)).structuredContent());

        int soma = 0;
        for (JsonNode trecho : estruturado.get("trechos")) {
            assertThat(trecho.get("texto").asString().length()).isLessThanOrEqualTo(2_000);
            soma += trecho.get("texto").asString().length();
        }
        assertThat(soma).isLessThanOrEqualTo(8_000);
        assertThat(estruturado.get("total").asInt()).isEqualTo(4);
    }

    @Test
    void trechoNaoPublicadoOutroModeloEVencidoNaoSaemPelaFerramenta() {
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("publicado", 0.90));
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("rascunho", 0.99).comStatus("EM_REVISAO"));
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("outro-modelo", 0.98).comModelo("modelo-antigo"));
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("vencido", 0.97).comVigencia(20_200_101));

        JsonNode estruturado = json.valueToTree(chamar(Map.of("pergunta", PERGUNTA)).structuredContent());

        assertThat(estruturado.get("trechos")).hasSize(1);
        assertThat(estruturado.get("trechos").get(0).get("texto").asString()).isEqualTo("publicado");
    }

    @Test
    void aSaidaNuncaTrazMetadadosInternosNemVetor() {
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("conteúdo", 0.9));

        String saida = texto(chamar(Map.of("pergunta", PERGUNTA)));

        assertThat(saida).doesNotContain("status", "modeloEmbedding", "vigenteAte", "ordem", "embedding",
                "PUBLICADO", "text-embedding");
    }

    @Test
    void asBuscasSucessivasNaMesmaSessaoFuncionam() {
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("conteúdo", 0.9));

        for (int i = 0; i < 3; i++) {
            assertThat(chamar(Map.of("pergunta", PERGUNTA)).isError()).isFalse();
        }
    }

    // ---- argumentos inválidos (§4.5)

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "   ", "  a  "})
    void perguntaCurtaOuVaziaDevolveArgumentosInvalidos(String pergunta) {
        JsonNode corpo = erro(chamar(Map.of("pergunta", pergunta)));

        assertThat(corpo.get("codigo").asString()).isEqualTo("ARGUMENTOS_INVALIDOS");
        assertThat(corpo.get("mensagem").asString()).isEqualTo("O campo 'pergunta' deve ter entre 3 e 1.000 caracteres.");
        assertThat(embeddings.chamadas()).as("rejeitada antes de gerar embedding").isZero();
    }

    @Test
    void perguntaComMaisDeMilCaracteresDevolveArgumentosInvalidosSemEcoDoValor() {
        String longa = "z".repeat(1_001);

        CallToolResult resultado = chamar(Map.of("pergunta", longa));

        assertThat(erro(resultado).get("codigo").asString()).isEqualTo("ARGUMENTOS_INVALIDOS");
        assertThat(texto(resultado)).doesNotContain("zzzzzzzzzz");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 11, 1000})
    void limiteForaDeUmADezDevolveArgumentosInvalidos(int limite) {
        JsonNode corpo = erro(chamar(Map.of("pergunta", PERGUNTA, "limite", limite)));

        assertThat(corpo.get("codigo").asString()).isEqualTo("ARGUMENTOS_INVALIDOS");
        assertThat(corpo.get("mensagem").asString()).isEqualTo("O campo 'limite' deve estar entre 1 e 10.");
        assertThat(embeddings.chamadas()).isZero();
    }

    @Test
    void divergenciaConhecida_violacaoDoSchemaEBarradaPeloSdkComTextoQueNaoEErroFerramenta() {
        // Tipo errado ou campo obrigatório ausente é recusado pelo SDK antes do método. É isError=true, sem
        // o valor recebido, mas o texto não é o JSON ErroFerramenta. O cliente da api-atendimentos trata qualquer
        // isError como falha, então o formato não importa para ele.
        CallToolResult tipoErrado = chamar(Map.of("pergunta", PERGUNTA, "limite", "abc"));
        CallToolResult semPergunta = chamar(Map.of("limite", 3));

        for (CallToolResult resultado : List.of(tipoErrado, semPergunta)) {
            assertThat(resultado.isError()).isTrue();
            assertThat(texto(resultado)).doesNotContain("ARGUMENTOS_INVALIDOS").doesNotContain("abc");
        }
        assertThat(embeddings.chamadas()).isZero();
    }

    @Test
    void argumentoDesconhecidoEIgnoradoPeloServidor() {
        // O contrato pede additionalProperties:false; o servidor é tolerante e ignora o que não conhece.
        CallToolResult resultado = chamar(Map.of("pergunta", PERGUNTA, "status", "EM_REVISAO"));

        assertThat(resultado.isError()).isFalse();
    }

    // ---- falhas de dependências (§4.5)

    @Test
    void bancoIndisponivelDevolveBaseConhecimentoIndisponivelSemDetalheInterno() {
        doThrow(new DataAccessResourceFailureException("conexão recusada em jdbc:postgresql://interno:5432/segredo"))
                .when(vectorStore).similaritySearch(any(SearchRequest.class));

        CallToolResult resultado = chamar(Map.of("pergunta", PERGUNTA));

        JsonNode corpo = erro(resultado);
        assertThat(corpo.get("codigo").asString()).isEqualTo("BASE_CONHECIMENTO_INDISPONIVEL");
        assertThat(corpo.get("mensagem").asString()).isEqualTo("A base de conhecimento está temporariamente indisponível.");
        assertMensagemSegura(texto(resultado));
        assertThat(texto(resultado)).doesNotContain("jdbc", "segredo", "interno");
    }

    @Test
    void embeddingIndisponivelDevolveModeloIndisponivel() {
        embeddings.falharSempre(EmbeddingModelFalso::limiteDeRequisicoes);

        CallToolResult resultado = chamar(Map.of("pergunta", PERGUNTA));

        assertThat(erro(resultado).get("codigo").asString()).isEqualTo("MODELO_INDISPONIVEL");
        assertMensagemSegura(texto(resultado));
    }

    @Test
    void credencialRecusadaPelaOpenAiTambemEhModeloIndisponivelSemVazarAChave() {
        embeddings.falharSempre(EmbeddingModelFalso::credencialRecusada);

        CallToolResult resultado = chamar(Map.of("pergunta", PERGUNTA));

        assertThat(erro(resultado).get("codigo").asString()).isEqualTo("MODELO_INDISPONIVEL");
        assertThat(texto(resultado)).doesNotContain(ChavesTeste.OPENAI, "401", "Unauthorized");
    }

    @Test
    void embeddingMaisLentoQueOTempoLimiteDevolveModeloIndisponivelDentroDoPrazo() {
        embeddings.atrasar(Duration.ofSeconds(8));
        long inicio = System.nanoTime();

        CallToolResult resultado = chamar(Map.of("pergunta", PERGUNTA));

        assertThat(erro(resultado).get("codigo").asString()).isEqualTo("MODELO_INDISPONIVEL");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void erroInesperadoDevolveErroInternoSemRevelarDetalhe() {
        doThrow(new IllegalStateException("detalhe interno secreto da classe X"))
                .when(vectorStore).similaritySearch(any(SearchRequest.class));

        CallToolResult resultado = chamar(Map.of("pergunta", PERGUNTA));

        JsonNode corpo = erro(resultado);
        assertThat(corpo.get("codigo").asString()).isEqualTo("ERRO_INTERNO");
        assertThat(corpo.get("mensagem").asString()).isEqualTo("Ocorreu um erro inesperado ao buscar conhecimento.");
        assertThat(texto(resultado)).doesNotContain("secreto", "classe X");
        assertMensagemSegura(texto(resultado));
    }

    @Test
    void depoisDeUmaFalhaAMesmaSessaoContinuaFuncionando() {
        embeddings.falharAsProximas(1, EmbeddingModelFalso::erroDoServidor);
        assertThat(chamar(Map.of("pergunta", PERGUNTA)).isError()).isTrue();

        assertThat(chamar(Map.of("pergunta", PERGUNTA)).isError()).isFalse();
    }

    private static void assertMensagemSegura(String texto) {
        assertThat(texto).doesNotContain("Exception", "br.com.coti", "org.springframework", "\tat ", "SELECT",
                "vector_store");
    }

    // ---- ferramenta inexistente e credencial

    @Test
    void ferramentaInexistenteNaoExecutaNada() {
        CallToolResult resultado;
        try {
            resultado = cliente.callTool(new CallToolRequest("apagar_tudo", Map.of()));
        } catch (RuntimeException e) {
            // erro JSON-RPC definido pelo SDK (por exemplo, -32602)
            assertThat(String.valueOf(e.getMessage())).doesNotContain("br.com.coti");
            return;
        }
        assertThat(resultado.isError()).isTrue();
    }

    @Test
    void semCredencialOuComChaveErradaOuAdministrativaAInicializacaoFalha() {
        for (String bearer : new String[] {null, "chave-errada", ChavesTeste.ADMIN, ChavesTeste.MONITORAMENTO}) {
            assertThatThrownBy(() -> ClienteMcpDeTeste.criar(porta, bearer).close())
                    .as("bearer=%s", bearer == null ? "(nenhum)" : "***").isInstanceOf(RuntimeException.class);
        }
        assertThat(embeddings.chamadas()).isZero();
    }
}
