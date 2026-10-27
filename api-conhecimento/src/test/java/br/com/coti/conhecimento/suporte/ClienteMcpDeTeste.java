package br.com.coti.conhecimento.suporte;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import java.net.http.HttpRequest;
import java.time.Duration;

/** Cliente MCP síncrono por Streamable HTTP, como a api-atendimentos o usará, para os testes de contrato. */
public final class ClienteMcpDeTeste {

    private ClienteMcpDeTeste() {
    }

    /** Cliente inicializado; quem chama fecha com {@code close()}. {@code bearer} nulo não envia credencial. */
    public static McpSyncClient criar(int porta, String bearer) {
        HttpRequest.Builder requisicao = HttpRequest.newBuilder();
        if (bearer != null) {
            requisicao.header("Authorization", "Bearer " + bearer);
        }
        HttpClientStreamableHttpTransport transporte = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + porta)
                .endpoint("/mcp")
                .requestBuilder(requisicao)
                .openConnectionOnStartup(false)
                .build();
        McpSyncClient cliente = McpClient.sync(transporte)
                .requestTimeout(Duration.ofSeconds(15))
                .initializationTimeout(Duration.ofSeconds(15))
                .build();
        cliente.initialize();
        return cliente;
    }
}
