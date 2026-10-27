package br.com.coti.atendimentos.suporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** O servidor de teste fala o protocolo MCP de verdade: um cliente do SDK o inicializa, lista e chama a ferramenta. */
class ServidorMcpDeTesteTest {

    private ServidorMcpDeTeste servidor;
    private McpSyncClient cliente;

    @BeforeEach
    void iniciar() {
        servidor = ServidorMcpDeTeste.iniciar().exigindoBearer("chave-de-teste");
        cliente = criar("chave-de-teste");
    }

    @AfterEach
    void encerrar() {
        cliente.close();
        servidor.close();
    }

    private McpSyncClient criar(String chave) {
        HttpRequest.Builder requisicao = HttpRequest.newBuilder().header("Authorization", "Bearer " + chave);
        return McpClient.sync(HttpClientStreamableHttpTransport.builder(servidor.url()).endpoint("/mcp")
                        .requestBuilder(requisicao).build())
                .requestTimeout(Duration.ofSeconds(5)).initializationTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void inicializaEPublicaAFerramentaDoContrato() {
        cliente.initialize();

        assertThat(cliente.getServerInfo().name()).isEqualTo("coti-conhecimento");
        var ferramentas = cliente.listTools().tools();
        assertThat(ferramentas).hasSize(1);
        var ferramenta = ferramentas.get(0);
        assertThat(ferramenta.name()).isEqualTo("buscar_conhecimento");
        assertThat(ferramenta.annotations().readOnlyHint()).isTrue();
        assertThat(ferramenta.annotations().destructiveHint()).isFalse();
        assertThat(ferramenta.outputSchema()).containsKey("properties");
    }

    @Test
    void respondeComDoisTrechosPorPadrao() {
        CallToolResult resultado = cliente.callTool(new CallToolRequest("buscar_conhecimento",
                Map.of("pergunta", "qualquer pergunta", "limite", 5)));

        assertThat(resultado.isError()).isFalse();
        assertThat(resultado.structuredContent()).isNotNull();
        assertThat(servidor.argumentosDaChamada(0).get("pergunta").asString()).isEqualTo("qualquer pergunta");
        assertThat(servidor.argumentosDaChamada(0).get("limite").asInt()).isEqualTo(5);
    }

    @Test
    void sobSemFerramentaListaVazia() {
        servidor.semAFerramenta();
        cliente.initialize();

        assertThat(cliente.listTools().tools()).isEmpty();
    }

    @Test
    void recusaChaveErradaCom401() {
        try (McpSyncClient errado = criar("chave-errada")) {
            assertThatThrownBy(errado::initialize).isInstanceOf(RuntimeException.class);
        }
        assertThat(servidor.requisicoes()).anyMatch(r -> r.cabecalho("authorization").equals("Bearer chave-errada"));
    }

    @Test
    void registraOsCabecalhosRecebidos() {
        cliente.initialize();

        assertThat(servidor.requisicoes()).isNotEmpty()
                .allSatisfy(r -> assertThat(r.cabecalho("authorization")).isEqualTo("Bearer chave-de-teste"));
    }

    @Test
    void reiniciarVoltaAoComportamentoPadrao() {
        servidor.respondendoVazio().semAFerramenta();

        servidor.reiniciar();

        assertThat(servidor.requisicoes()).isEmpty();
        cliente.initialize();
        assertThat(cliente.listTools().tools()).hasSize(1);
    }
}
