package br.com.coti.atendimentos.infraestrutura.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.common.McpTransportContext;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class McpCabecalhosCustomizerTest {

    private final McpCabecalhosCustomizer customizer = new McpCabecalhosCustomizer("chave-secreta");

    private HttpRequest montar(McpTransportContext contexto) {
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create("http://localhost/mcp"));
        customizer.customize(requisicao, "POST", URI.create("http://localhost/mcp"), "{}", contexto);
        return requisicao.POST(HttpRequest.BodyPublishers.noBody()).build();
    }

    @Test
    void sempreEnviaOBearerDaChaveDeServico() {
        assertThat(montar(McpTransportContext.EMPTY).headers().firstValue("Authorization"))
                .contains("Bearer chave-secreta");
    }

    @Test
    void comTraceIdValidoEnviaOTraceparentW3c() {
        HttpRequest requisicao = montar(McpTransportContext.create(Map.of("traceId", "4bf92f3577b34da6a3ce929d0e0e4736")));

        assertThat(requisicao.headers().firstValue("traceparent").orElseThrow())
                .matches("00-4bf92f3577b34da6a3ce929d0e0e4736-[0-9a-f]{16}-01");
    }

    @Test
    void cadaRequisicaoTemUmSpanIdDiferente() {
        McpTransportContext contexto = McpTransportContext.create(Map.of("traceId", "4bf92f3577b34da6a3ce929d0e0e4736"));

        String a = montar(contexto).headers().firstValue("traceparent").orElseThrow();
        String b = montar(contexto).headers().firstValue("traceparent").orElseThrow();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void semTraceIdNaoEnviaTraceparent() {
        assertThat(montar(McpTransportContext.EMPTY).headers().firstValue("traceparent")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"curto", "4BF92F3577B34DA6A3CE929D0E0E4736", "zzf92f3577b34da6a3ce929d0e0e4736",
            "00000000000000000000000000000000", "4bf92f3577b34da6a3ce929d0e0e4736-extra"})
    void traceIdInvalidoNaoViraTraceparent(String traceId) {
        assertThat(montar(McpTransportContext.create(Map.of("traceId", traceId))).headers()
                .firstValue("traceparent")).isEmpty();
    }

    @Test
    void aChaveNuncaApareceNoToString() {
        assertThat(customizer.toString()).doesNotContain("chave-secreta");
    }
}
