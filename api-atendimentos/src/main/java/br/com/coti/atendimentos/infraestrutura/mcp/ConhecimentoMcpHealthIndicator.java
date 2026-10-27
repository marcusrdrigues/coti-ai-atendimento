package br.com.coti.atendimentos.infraestrutura.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

/**
 * Saúde da dependência {@code api-conhecimento} (contrato, seção 5.2): lista as ferramentas do servidor MCP com
 * tempo limite de 2 s e confere que {@code buscar_conhecimento} existe. Não chama a OpenAI.
 *
 * <p>Com a base fora do ar o componente fica {@code DEGRADADO}, e não {@code DOWN}: o Actuator agrega pelo pior
 * status, e {@code DOWN} derrubaria o status do serviço (503) mesmo ele estando de pé e respondendo à pergunta
 * com o erro do contrato. {@code DEGRADADO} entra no meio da ordem de status, com HTTP 200. O resultado fica
 * em cache por alguns segundos para o endpoint público de saúde não virar uma chamada ao MCP por requisição.
 * Os detalhes nunca trazem URL, host, porta nem credencial.
 */
public final class ConhecimentoMcpHealthIndicator implements HealthIndicator, AutoCloseable {

    public static final Status DEGRADADO = new Status("DEGRADADO");

    private static final Logger log = LoggerFactory.getLogger(ConhecimentoMcpHealthIndicator.class);

    private final McpSyncClient cliente;
    private final Duration tempoLimite;
    private final Duration validade;
    private final Clock relogio;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private Health ultimo;
    private Instant verificadoEm;

    public ConhecimentoMcpHealthIndicator(List<McpSyncClient> clientes, Duration tempoLimite, Duration validade,
                                          Clock relogio) {
        if (clientes.size() != 1) {
            throw new IllegalStateException("Era esperada exatamente 1 conexão MCP (conhecimento), mas há "
                    + clientes.size() + ".");
        }
        this.cliente = clientes.get(0);
        this.tempoLimite = tempoLimite;
        this.validade = validade;
        this.relogio = relogio;
    }

    @Override
    public synchronized Health health() {
        Instant agora = relogio.instant();
        if (ultimo != null && verificadoEm.plus(validade).isAfter(agora)) {
            return ultimo;
        }
        ultimo = verificar();
        verificadoEm = agora;
        return ultimo;
    }

    private Health verificar() {
        Future<List<Tool>> futuro = executor.submit(() -> cliente.listTools().tools());
        try {
            List<Tool> ferramentas = futuro.get(tempoLimite.toMillis(), TimeUnit.MILLISECONDS);
            if (ferramentas.stream().anyMatch(t -> BaseConhecimentoMcpAdapter.FERRAMENTA.equals(t.name()))) {
                return Health.up().build();
            }
            log.warn("A api-conhecimento não publica a ferramenta {}.", BaseConhecimentoMcpAdapter.FERRAMENTA);
            return indisponivel("FERRAMENTA_AUSENTE");
        } catch (InterruptedException e) {
            futuro.cancel(false);
            Thread.currentThread().interrupt();
            return indisponivel("BASE_CONHECIMENTO_INDISPONIVEL");
        } catch (Exception e) {
            futuro.cancel(false);
            log.warn("A api-conhecimento não respondeu à verificação de saúde. tipo={}", e.getClass().getSimpleName());
            return indisponivel("BASE_CONHECIMENTO_INDISPONIVEL");
        }
    }

    private static Health indisponivel(String codigo) {
        return Health.status(DEGRADADO).withDetail("codigo", codigo).build();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
