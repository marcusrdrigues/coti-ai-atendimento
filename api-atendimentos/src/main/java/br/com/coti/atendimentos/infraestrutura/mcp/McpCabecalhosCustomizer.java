package br.com.coti.atendimentos.infraestrutura.mcp;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import java.net.URI;
import java.net.http.HttpRequest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Acrescenta a toda requisição HTTP ao servidor MCP a credencial de serviço ({@code Authorization: Bearer}) e o
 * contexto de trace ({@code traceparent}, W3C). O trace id vem do contexto de transporte
 * ({@link McpContextoTraceCustomizer}), porque as requisições saem em threads do cliente HTTP, onde o MDC da
 * requisição original não existe. A chave nunca é registrada em log.
 */
public final class McpCabecalhosCustomizer implements McpSyncHttpClientRequestCustomizer {

    static final String CHAVE_TRACE = "traceId";

    private static final Pattern TRACE_ID = Pattern.compile("^[0-9a-f]{32}$");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final String chave;

    public McpCabecalhosCustomizer(String chave) {
        this.chave = chave;
    }

    @Override
    public void customize(HttpRequest.Builder requisicao, String metodo, URI endpoint, String corpo,
                          McpTransportContext contexto) {
        requisicao.header("Authorization", "Bearer " + chave);
        Object traceId = contexto == null ? null : contexto.get(CHAVE_TRACE);
        if (traceId instanceof String id && TRACE_ID.matcher(id).matches() && !id.chars().allMatch(c -> c == '0')) {
            requisicao.header("traceparent", "00-" + id + "-" + novoSpanId() + "-01");
        }
    }

    private static String novoSpanId() {
        byte[] bytes = new byte[8];
        do {
            ALEATORIO.nextBytes(bytes);
        } while (todosZero(bytes));
        return HexFormat.of().formatHex(bytes);
    }

    private static boolean todosZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "McpCabecalhosCustomizer[chave=***]";
    }
}
