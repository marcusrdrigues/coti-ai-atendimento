package br.com.coti.atendimentos.infraestrutura.mcp;

import br.com.coti.atendimentos.api.erro.CorrelacaoHttp;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;

/**
 * Faz o cliente MCP ler, na thread que chama a ferramenta, o trace id do MDC e levá-lo ao contexto de transporte,
 * de onde o {@link McpCabecalhosCustomizer} o copia para o {@code traceparent}.
 */
public final class McpContextoTraceCustomizer implements McpClientCustomizer<McpClient.SyncSpec> {

    @Override
    public void customize(String nomeDaConexao, McpClient.SyncSpec cliente) {
        cliente.transportContextProvider(() -> {
            String traceId = MDC.get(CorrelacaoHttp.MDC_TRACE);
            return traceId == null
                    ? McpTransportContext.EMPTY
                    : McpTransportContext.create(Map.of(McpCabecalhosCustomizer.CHAVE_TRACE, traceId));
        });
    }
}
