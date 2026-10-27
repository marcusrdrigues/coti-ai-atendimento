package br.com.coti.atendimentos.infraestrutura.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class ConhecimentoMcpHealthIndicatorTest {

    /** Relógio que anda só quando o teste manda. */
    private static final class RelogioManual extends Clock {
        private Instant agora = Instant.parse("2026-10-27T12:00:00Z");

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final McpSyncClient cliente = mock(McpSyncClient.class);
    private final RelogioManual relogio = new RelogioManual();
    private ConhecimentoMcpHealthIndicator indicador;

    private ConhecimentoMcpHealthIndicator criar(Duration validade) {
        indicador = new ConhecimentoMcpHealthIndicator(List.of(cliente), Duration.ofSeconds(1), validade, relogio);
        return indicador;
    }

    @AfterEach
    void fechar() {
        if (indicador != null) {
            indicador.close();
        }
    }

    private static ListToolsResult ferramentas(String... nomes) {
        List<Tool> tools = java.util.Arrays.stream(nomes)
                .map(n -> new Tool(n, null, "d", Map.of("type", "object"), null, null, null, null)).toList();
        return new ListToolsResult(tools, null);
    }

    @Test
    void upQuandoAFerramentaDeBuscaExiste() {
        when(cliente.listTools()).thenReturn(ferramentas("buscar_conhecimento"));

        assertThat(criar(Duration.ofSeconds(10)).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void degradadoQuandoAFerramentaNaoExiste() {
        when(cliente.listTools()).thenReturn(ferramentas("outra_ferramenta"));

        Health saude = criar(Duration.ofSeconds(10)).health();

        assertThat(saude.getStatus()).isEqualTo(ConhecimentoMcpHealthIndicator.DEGRADADO);
        assertThat(saude.getDetails()).containsEntry("codigo", "FERRAMENTA_AUSENTE");
    }

    @Test
    void degradadoQuandoOClienteFalhaSemVazarAMensagem() {
        when(cliente.listTools()).thenThrow(new RuntimeException("conexão com http://interno:8081 falhou"));

        Health saude = criar(Duration.ofSeconds(10)).health();

        assertThat(saude.getStatus()).isEqualTo(ConhecimentoMcpHealthIndicator.DEGRADADO);
        assertThat(saude.getDetails()).containsOnlyKeys("codigo").doesNotContainValue("http://interno:8081");
    }

    @Test
    void oResultadoFicaEmCacheEnquantoValido() {
        when(cliente.listTools()).thenReturn(ferramentas("buscar_conhecimento"));
        ConhecimentoMcpHealthIndicator i = criar(Duration.ofSeconds(10));

        i.health();
        relogio.avancar(Duration.ofSeconds(9));
        i.health();
        i.health();

        verify(cliente, times(1)).listTools();
    }

    @Test
    void oResultadoRuimFicaEmCacheSoPorDoisSegundosParaAVoltaAoUpNaoDemorar() {
        when(cliente.listTools()).thenThrow(new RuntimeException("sessao perdida")).thenReturn(ferramentas("buscar_conhecimento"));
        ConhecimentoMcpHealthIndicator i = criar(Duration.ofSeconds(10));

        assertThat(i.health().getStatus()).isEqualTo(ConhecimentoMcpHealthIndicator.DEGRADADO);
        relogio.avancar(Duration.ofSeconds(1));
        assertThat(i.health().getStatus()).as("ainda em cache").isEqualTo(ConhecimentoMcpHealthIndicator.DEGRADADO);
        relogio.avancar(Duration.ofSeconds(1));

        assertThat(i.health().getStatus()).as("2 s depois verifica de novo e volta a UP").isEqualTo(Status.UP);
    }

    @Test
    void verificaDeNovoDepoisDaValidade() {
        when(cliente.listTools()).thenReturn(ferramentas("buscar_conhecimento"));
        ConhecimentoMcpHealthIndicator i = criar(Duration.ofSeconds(10));

        i.health();
        relogio.avancar(Duration.ofSeconds(10));
        i.health();

        verify(cliente, times(2)).listTools();
    }

    @Test
    void exigeExatamenteUmaConexaoMcp() {
        assertThatThrownBy(() -> new ConhecimentoMcpHealthIndicator(List.of(), Duration.ofSeconds(1),
                Duration.ofSeconds(1), relogio)).isInstanceOf(IllegalStateException.class);
    }
}
