package br.com.coti.conhecimento.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.coti.conhecimento.suporte.ChavesTeste;
import br.com.coti.conhecimento.suporte.ClienteMcpDeTeste;
import br.com.coti.conhecimento.suporte.EmbeddingModelFalso;
import br.com.coti.conhecimento.suporte.TesteServidor;
import br.com.coti.conhecimento.suporte.TrechosDeTeste;
import br.com.coti.conhecimento.suporte.TrechosDeTeste.Trecho;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * RF12: o diagnóstico REST e a ferramenta MCP executam o mesmo caso de uso. Para a mesma pergunta devolvem os mesmos
 * trechos, na mesma ordem, com as mesmas similaridades. Qualquer divergência entre o que o gestor vê e o que o
 * atendimento recebe invalidaria o diagnóstico.
 */
@TesteServidor
class DiagnosticoIgualMcpIT {

    private static final String PERGUNTA = "Preciso saber programar antes do curso de Java?";

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EmbeddingModelFalso embeddings;

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();
    private McpSyncClient mcp;

    @BeforeEach
    void iniciar() {
        TrechosDeTeste.zerar();
        embeddings.reiniciar();
        mcp = ClienteMcpDeTeste.criar(porta, ChavesTeste.MCP);
    }

    @AfterEach
    void encerrar() {
        mcp.close();
    }

    private JsonNode pelaFerramenta(String pergunta, Integer limite) {
        CallToolResult resultado = mcp.callTool(new CallToolRequest("buscar_conhecimento", argumentos(pergunta, limite)));
        assertThat(resultado.isError()).isFalse();
        return json.valueToTree(resultado.structuredContent());
    }

    private JsonNode peloDiagnostico(String pergunta, Integer limite) throws Exception {
        HttpRequest requisicao = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + porta + "/api/conhecimentos/diagnostico/busca"))
                .header("X-Api-Key", ChavesTeste.ADMIN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(argumentos(pergunta, limite)),
                        StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(resposta.statusCode()).isEqualTo(200);
        return json.readTree(resposta.body());
    }

    private static Map<String, Object> argumentos(String pergunta, Integer limite) {
        Map<String, Object> argumentos = new LinkedHashMap<>();
        argumentos.put("pergunta", pergunta);
        if (limite != null) {
            argumentos.put("limite", limite);
        }
        return argumentos;
    }

    private void gravarVarios(String pergunta) {
        double[] cossenos = {0.97, 0.93, 0.90, 0.86, 0.82, 0.79, 0.75, 0.72, 0.40, 0.20};
        for (int i = 0; i < cossenos.length; i++) {
            TrechosDeTeste.gravar(jdbc, pergunta.strip(),
                    Trecho.publicado("Trecho " + i + " sobre pré-requisitos.", cossenos[i]));
        }
        TrechosDeTeste.gravar(jdbc, pergunta.strip(), Trecho.publicado("rascunho", 0.99).comStatus("EM_REVISAO"));
        TrechosDeTeste.gravar(jdbc, pergunta.strip(), Trecho.publicado("vencido", 0.98).comVigencia(20_200_101));
    }

    private static void assertMesmosTrechos(JsonNode diagnostico, JsonNode ferramenta) {
        assertThat(diagnostico.get("trechos")).isEqualTo(ferramenta.get("trechos"));
        assertThat(diagnostico.get("total")).isEqualTo(ferramenta.get("total"));
    }

    @Test
    void mesmaPerguntaDevolveOsMesmosTrechosNaMesmaOrdemComAsMesmasSimilaridades() throws Exception {
        gravarVarios(PERGUNTA);

        JsonNode diagnostico = peloDiagnostico(PERGUNTA, null);
        JsonNode ferramenta = pelaFerramenta(PERGUNTA, null);

        assertThat(ferramenta.get("trechos")).hasSize(5);
        assertMesmosTrechos(diagnostico, ferramenta);
    }

    @Test
    void igualParaCadaLimiteDeUmADez() throws Exception {
        gravarVarios(PERGUNTA);

        for (int limite = 1; limite <= 10; limite++) {
            assertMesmosTrechos(peloDiagnostico(PERGUNTA, limite), pelaFerramenta(PERGUNTA, limite));
        }
    }

    @Test
    void igualQuandoNaoHaTrechoRelevante() throws Exception {
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("distante", 0.30));

        JsonNode diagnostico = peloDiagnostico(PERGUNTA, null);
        JsonNode ferramenta = pelaFerramenta(PERGUNTA, null);

        assertThat(ferramenta.get("total").asInt()).isZero();
        assertMesmosTrechos(diagnostico, ferramenta);
    }

    @Test
    void igualQuandoOCorteDe8MilCaracteresRemoveTrechos() throws Exception {
        for (int i = 0; i < 5; i++) {
            TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("y".repeat(2_400), 0.95 - i * 0.02));
        }

        JsonNode diagnostico = peloDiagnostico(PERGUNTA, 10);
        JsonNode ferramenta = pelaFerramenta(PERGUNTA, 10);

        assertThat(ferramenta.get("total").asInt()).isEqualTo(4);
        assertMesmosTrechos(diagnostico, ferramenta);
    }

    @Test
    void igualParaOutraPerguntaEComEspacosNasPontas() throws Exception {
        String outra = "Qual o valor do curso de Angular?";
        gravarVarios(outra);

        assertMesmosTrechos(peloDiagnostico("  " + outra + "  ", 4), pelaFerramenta("  " + outra + "  ", 4));
        assertMesmosTrechos(peloDiagnostico(outra, 4), pelaFerramenta(outra, 4));
    }

    @Test
    void oDiagnosticoAcrescentaSoLimiarModeloEDuracao() throws Exception {
        gravarVarios(PERGUNTA);

        JsonNode diagnostico = peloDiagnostico(PERGUNTA, 2);

        assertThat(diagnostico.propertyNames()).containsExactlyInAnyOrder("trechos", "total", "limiarSimilaridade",
                "modeloEmbedding", "duracaoMs");
        assertThat(diagnostico.get("limiarSimilaridade").asDouble()).isEqualTo(0.70);
        assertThat(diagnostico.get("modeloEmbedding").asString()).isEqualTo("text-embedding-3-small");
    }
}
