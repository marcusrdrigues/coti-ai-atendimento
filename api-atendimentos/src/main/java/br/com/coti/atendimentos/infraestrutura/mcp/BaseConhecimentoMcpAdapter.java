package br.com.coti.atendimentos.infraestrutura.mcp;

import br.com.coti.atendimentos.aplicacao.porta.BaseConhecimentoPort;
import br.com.coti.atendimentos.dominio.Prazo;
import br.com.coti.atendimentos.dominio.TrechoConhecimento;
import br.com.coti.atendimentos.dominio.excecao.BaseConhecimentoIndisponivelException;
import br.com.coti.atendimentos.dominio.excecao.TempoEsgotadoException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.McpHttpClientTransportAuthorizationException;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Chama a ferramenta MCP {@code buscar_conhecimento} (RF07) pelo código, nunca pelo modelo. Cada tentativa vale no
 * máximo o menor entre o tempo limite do MCP e o que resta do {@link Prazo}. Falhas de transporte (timeout,
 * conexão, HTTP 5xx, sessão perdida) têm novas tentativas, pois a ferramenta é de leitura e idempotente, com
 * backoff exponencial e variação aleatória; 401 e 403, {@code isError} e resultado fora do contrato falham de
 * imediato. A pergunta nunca vai para o log.
 */
public final class BaseConhecimentoMcpAdapter implements BaseConhecimentoPort, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BaseConhecimentoMcpAdapter.class);
    static final String FERRAMENTA = "buscar_conhecimento";

    /** Parâmetros de tempo e de novas tentativas. */
    public record Politica(Duration tempoLimite, int novasTentativas, Duration atrasoInicial, double multiplicador,
                           Duration atrasoMaximo, Duration jitter) {
    }

    private final McpSyncClient cliente;
    private final ValidadorResultadoBusca validador;
    private final Politica politica;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public BaseConhecimentoMcpAdapter(List<McpSyncClient> clientes, ValidadorResultadoBusca validador, Politica politica) {
        if (clientes.size() != 1) {
            throw new IllegalStateException("Era esperada exatamente 1 conexão MCP (conhecimento), mas há "
                    + clientes.size() + ".");
        }
        this.cliente = clientes.get(0);
        this.validador = validador;
        this.politica = politica;
    }

    @Override
    public List<TrechoConhecimento> buscar(String perguntaMascarada, int limite, Prazo prazo) {
        CallToolRequest requisicao = new CallToolRequest(FERRAMENTA,
                Map.of("pergunta", perguntaMascarada, "limite", limite));
        Throwable ultimaFalha = null;
        for (int tentativa = 0; tentativa <= politica.novasTentativas(); tentativa++) {
            if (tentativa > 0) {
                esperar(tentativa, prazo);
            }
            Duration tempo = prazo.limitar(politica.tempoLimite());
            if (tempo.isZero()) {
                throw new TempoEsgotadoException();
            }
            try {
                return validador.validar(chamar(requisicao, tempo));
            } catch (FalhaTransitoria falha) {
                ultimaFalha = falha.getCause();
                log.warn("Falha transitória na chamada MCP. tentativa={} tipo={}", tentativa + 1,
                        ultimaFalha.getClass().getSimpleName());
                if (prazo.expirado()) {
                    throw new TempoEsgotadoException();
                }
            }
        }
        throw new BaseConhecimentoIndisponivelException(
                "A api-conhecimento não respondeu após " + (politica.novasTentativas() + 1) + " tentativas.",
                ultimaFalha);
    }

    /**
     * Chamada isolada em uma thread própria para esta tentativa poder parar de esperar no tempo que lhe cabe. A
     * thread NUNCA é interrompida: interromper o cliente MCP durante a conexão inicial (preguiçosa) o deixa
     * travado para as chamadas seguintes (spike T044). Ela termina sozinha pelo {@code request-timeout} do SDK.
     */
    private CallToolResult chamar(CallToolRequest requisicao, Duration tempo) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Future<CallToolResult> futuro = executor.submit(() -> {
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try {
                return cliente.callTool(requisicao);
            } finally {
                MDC.clear();
            }
        });
        try {
            return futuro.get(tempo.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futuro.cancel(false);
            throw new FalhaTransitoria(e);
        } catch (InterruptedException e) {
            futuro.cancel(false);
            Thread.currentThread().interrupt();
            throw new BaseConhecimentoIndisponivelException("A chamada à api-conhecimento foi interrompida.", e);
        } catch (ExecutionException e) {
            throw classificar(e.getCause());
        }
    }

    private static RuntimeException classificar(Throwable falha) {
        if (procurar(falha, McpHttpClientTransportAuthorizationException.class) != null) {
            // Erro de configuração, não do aluno: precisa de alguém olhando. Nunca registra a chave.
            log.error("A api-conhecimento recusou a credencial de serviço (HTTP 401/403). "
                    + "Verifique CONHECIMENTO_MCP_API_KEY nos dois serviços.");
            return new BaseConhecimentoIndisponivelException("A api-conhecimento recusou a credencial de serviço.", falha);
        }
        // Qualquer outra falha de transporte (timeout, conexão recusada, HTTP 5xx, sessão MCP perdida porque a
        // api-conhecimento reiniciou) vale nova tentativa: a ferramenta é de leitura e idempotente. O SDK sinaliza a
        // sessão perdida só com uma RuntimeException de mensagem fixa, sem tipo próprio, então não dá para distinguir.
        return new FalhaTransitoria(falha);
    }

    private static Throwable procurar(Throwable falha, Class<? extends Throwable> tipo) {
        for (Throwable atual = falha; atual != null; atual = atual.getCause() == atual ? null : atual.getCause()) {
            if (tipo.isInstance(atual)) {
                return atual;
            }
        }
        return null;
    }

    /** Backoff exponencial com variação aleatória, sempre limitado pelo que resta do prazo. */
    private void esperar(int tentativa, Prazo prazo) {
        double base = politica.atrasoInicial().toMillis() * Math.pow(politica.multiplicador(), tentativa - 1);
        long atraso = Math.min((long) base, politica.atrasoMaximo().toMillis());
        long jitter = politica.jitter().toMillis();
        if (jitter > 0) {
            atraso += ThreadLocalRandom.current().nextLong(jitter + 1);
        }
        if (Duration.ofMillis(atraso).compareTo(prazo.restante()) >= 0) {
            throw new TempoEsgotadoException();
        }
        try {
            Thread.sleep(atraso);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseConhecimentoIndisponivelException("A chamada à api-conhecimento foi interrompida.", e);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    /** Timeout ou falha de conexão: vale tentar de novo. */
    private static final class FalhaTransitoria extends RuntimeException {

        private static final long serialVersionUID = 1L;

        FalhaTransitoria(Throwable causa) {
            super(causa);
        }
    }
}
