package br.com.coti.atendimentos.infraestrutura.ia;

import br.com.coti.atendimentos.aplicacao.porta.GeradorRespostaPort;
import br.com.coti.atendimentos.dominio.ContextoRag;
import br.com.coti.atendimentos.dominio.Prazo;
import br.com.coti.atendimentos.dominio.RespostaGerada;
import br.com.coti.atendimentos.dominio.excecao.ModeloIndisponivelException;
import br.com.coti.atendimentos.dominio.excecao.SaidaModeloInvalidaException;
import br.com.coti.atendimentos.dominio.excecao.TempoEsgotadoException;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;

/**
 * Gera a resposta com o {@link ChatClient} (OpenAI), atrás da porta da aplicação. O {@code ChatClient} não tem
 * ferramentas: o modelo só recebe o contexto que o código montou e devolve a saída estruturada nativa
 * ({@link RespostaModelo}). Temperatura, limite de tokens e modelo vêm de configuração, nunca daqui.
 *
 * <p>Falha transitória (timeout, 429, 5xx, rede) tem {@code novasTentativas} com backoff exponencial e variação;
 * 400, 401 e 403 não repetem (401 e 403 geram alerta de configuração, sem a chave). Cada tentativa vale no máximo o
 * menor entre o tempo limite e o que resta do {@link Prazo}. Saída que não converte vira
 * {@link SaidaModeloInvalidaException}: nunca é remendada. O prompt, a pergunta e a resposta não vão para log.
 */
public final class OpenAiGeradorRespostaAdapter implements GeradorRespostaPort, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OpenAiGeradorRespostaAdapter.class);

    /** Tempo e novas tentativas da chamada ao modelo. */
    public record Politica(Duration tempoLimite, int novasTentativas, Duration atrasoInicial, double multiplicador,
                           Duration atrasoMaximo, Duration jitter) {
    }

    private final ChatClient chatClient;
    private final Resource promptSistema;
    private final Resource promptUsuario;
    private final Politica politica;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public OpenAiGeradorRespostaAdapter(ChatClient chatClient, Resource promptSistema, Resource promptUsuario,
                                        Politica politica) {
        this.chatClient = chatClient;
        this.promptSistema = promptSistema;
        this.promptUsuario = promptUsuario;
        this.politica = politica;
    }

    @Override
    public RespostaGerada gerar(String perguntaMascarada, ContextoRag contexto, String motivoReprovacao, Prazo prazo) {
        String aviso = motivoReprovacao == null ? ""
                : "Atenção: a sua resposta anterior foi reprovada. Motivo: " + motivoReprovacao
                        + " Corrija e responda de novo no mesmo formato.";
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
                return converter(chamar(perguntaMascarada, contexto, aviso, tempo));
            } catch (FalhaTransitoria falha) {
                ultimaFalha = falha.getCause();
                log.warn("Falha transitória na chamada ao modelo. tentativa={} tipo={}", tentativa + 1,
                        ultimaFalha.getClass().getSimpleName());
                if (prazo.expirado()) {
                    throw new TempoEsgotadoException();
                }
            }
        }
        throw new ModeloIndisponivelException("O modelo não respondeu após " + (politica.novasTentativas() + 1)
                + " tentativas.", ultimaFalha);
    }

    private RespostaModelo chamar(String pergunta, ContextoRag contexto, String aviso, Duration tempo) {
        Future<RespostaModelo> futuro = executor.submit(() -> chatClient.prompt()
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .system(promptSistema)
                .user(u -> u.text(promptUsuario)
                        .param("trechos", contexto.bloco())
                        .param("pergunta", pergunta)
                        .param("aviso", aviso))
                .call()
                .entity(RespostaModelo.class));
        try {
            return futuro.get(tempo.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futuro.cancel(true);
            throw new FalhaTransitoria(e);
        } catch (InterruptedException e) {
            futuro.cancel(true);
            Thread.currentThread().interrupt();
            throw new ModeloIndisponivelException("A chamada ao modelo foi interrompida.", e);
        } catch (ExecutionException e) {
            throw classificar(e.getCause());
        }
    }

    private static RespostaGerada converter(RespostaModelo modelo) {
        if (modelo == null) {
            throw new SaidaModeloInvalidaException("O modelo não devolveu conteúdo.", null);
        }
        return new RespostaGerada(modelo.resposta(), modelo.conhecimentoSuficiente(), modelo.referencias());
    }

    /**
     * Falha do SDK da OpenAI: transitória (conexão, timeout, 408, 429, 5xx) ou definitiva (400, 401, 403, 404...).
     * Qualquer outra falha vinda da chamada é a conversão da saída para o schema: saída inválida.
     */
    private static RuntimeException classificar(Throwable falha) {
        OpenAIException sdk = procurarSdk(falha);
        if (sdk == null) {
            return new SaidaModeloInvalidaException("A saída do modelo não está no formato esperado.", falha);
        }
        if (transitoria(sdk)) {
            return new FalhaTransitoria(sdk);
        }
        if (sdk instanceof OpenAIServiceException servico && (servico.statusCode() == 401 || servico.statusCode() == 403)) {
            // Erro de configuração, não do aluno: precisa de alguém olhando. Nunca registra a chave.
            log.error("A OpenAI recusou a credencial (HTTP {}). Verifique OPENAI_API_KEY e as permissões da conta.",
                    servico.statusCode());
        }
        return new ModeloIndisponivelException("O modelo recusou a requisição (" + sdk.getClass().getSimpleName() + ").",
                sdk);
    }

    private static boolean transitoria(OpenAIException sdk) {
        if (sdk instanceof OpenAIIoException || sdk instanceof OpenAIRetryableException) {
            return true;
        }
        if (sdk instanceof OpenAIServiceException servico) {
            int status = servico.statusCode();
            return status == 408 || status == 429 || status >= 500;
        }
        return false;
    }

    private static OpenAIException procurarSdk(Throwable falha) {
        for (Throwable atual = falha; atual != null; atual = atual.getCause() == atual ? null : atual.getCause()) {
            if (atual instanceof OpenAIException sdk) {
                return sdk;
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
            throw new ModeloIndisponivelException("A chamada ao modelo foi interrompida.", e);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    /** Timeout ou falha de rede, 408, 429 ou 5xx: vale tentar de novo. */
    private static final class FalhaTransitoria extends RuntimeException {

        private static final long serialVersionUID = 1L;

        FalhaTransitoria(Throwable causa) {
            super(causa);
        }
    }
}
