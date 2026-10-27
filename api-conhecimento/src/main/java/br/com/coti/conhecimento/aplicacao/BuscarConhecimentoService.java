package br.com.coti.conhecimento.aplicacao;

import br.com.coti.conhecimento.aplicacao.porta.BuscaVetorialPort;
import br.com.coti.conhecimento.dominio.LimitesBusca;
import br.com.coti.conhecimento.dominio.ModeloEmbedding;
import br.com.coti.conhecimento.dominio.TrechoEncontrado;
import br.com.coti.conhecimento.dominio.excecao.ArgumentosInvalidosException;
import br.com.coti.conhecimento.dominio.excecao.ModeloIndisponivelException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caso de uso único da busca semântica (RF04), chamado pela ferramenta MCP e pelo diagnóstico administrativo.
 * Valida a entrada, busca na base (filtro de publicação, modelo e vigência feito pela própria consulta
 * vetorial), ordena por similaridade e limita o texto devolvido a 2.000 caracteres por trecho e 8.000 no total.
 *
 * <p>O tempo máximo da busca (que inclui o embedding da pergunta, a parte lenta) é aplicado aqui, e não no SDK,
 * para valer igual a qualquer provedor. A pergunta nunca é registrada em log.
 */
public final class BuscarConhecimentoService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BuscarConhecimentoService.class);

    private final BuscaVetorialPort busca;
    private final ModeloEmbedding modelo;
    private final LimitesBusca limites;
    private final Clock relogio;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public BuscarConhecimentoService(BuscaVetorialPort busca, ModeloEmbedding modelo, LimitesBusca limites,
                                     Clock relogio) {
        this.busca = busca;
        this.modelo = modelo;
        this.limites = limites;
        this.relogio = relogio;
    }

    /**
     * @param pergunta texto da pergunta; espaços nas pontas são ignorados
     * @param limite quantidade máxima de trechos; {@code null} usa o padrão
     */
    public ResultadoDaBusca buscar(String pergunta, Integer limite) {
        String perguntaValida = validarPergunta(pergunta);
        int limiteValido = validarLimite(limite);
        long inicio = System.nanoTime();

        List<TrechoEncontrado> encontrados = executarComTempoLimite(new BuscaVetorialPort.Consulta(
                perguntaValida, limiteValido, limites.limiarSimilaridade(), modelo.nome(), LocalDate.now(relogio)));
        List<TrechoEncontrado> trechos = limitarTexto(ordenar(encontrados));

        Duration duracao = Duration.ofNanos(System.nanoTime() - inicio);
        log.info("Busca concluída. trechos={} maiorSimilaridade={} duracaoMs={}", trechos.size(),
                trechos.isEmpty() ? "-" : trechos.get(0).similaridade(), duracao.toMillis());
        return new ResultadoDaBusca(trechos, limites.limiarSimilaridade(), modelo.nome(), duracao);
    }

    private String validarPergunta(String pergunta) {
        String limpa = pergunta == null ? "" : pergunta.strip();
        if (limpa.length() < limites.perguntaMinima() || limpa.length() > limites.perguntaMaxima()) {
            throw new ArgumentosInvalidosException("pergunta", "O campo 'pergunta' deve ter entre "
                    + limites.perguntaMinima() + " e " + milhar(limites.perguntaMaxima()) + " caracteres.");
        }
        return limpa;
    }

    private int validarLimite(Integer limite) {
        if (limite == null) {
            return limites.limitePadrao();
        }
        if (limite < 1 || limite > limites.limiteMaximo()) {
            throw new ArgumentosInvalidosException("limite",
                    "O campo 'limite' deve estar entre 1 e " + limites.limiteMaximo() + ".");
        }
        return limite;
    }

    private List<TrechoEncontrado> executarComTempoLimite(BuscaVetorialPort.Consulta consulta) {
        Future<List<TrechoEncontrado>> futuro = executor.submit(() -> busca.buscar(consulta));
        try {
            return futuro.get(limites.tempoLimite().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futuro.cancel(true);
            throw new ModeloIndisponivelException(e);
        } catch (InterruptedException e) {
            futuro.cancel(true);
            Thread.currentThread().interrupt();
            throw new ModeloIndisponivelException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException falha) {
                throw falha;
            }
            throw new IllegalStateException("Falha inesperada na busca.", e.getCause());
        }
    }

    /** Maior similaridade primeiro; sem score, mantém a ordem de relevância da busca (ordenação estável). */
    private static List<TrechoEncontrado> ordenar(List<TrechoEncontrado> trechos) {
        List<TrechoEncontrado> ordenados = new ArrayList<>(trechos);
        ordenados.sort(Comparator.comparing(TrechoEncontrado::similaridade,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return ordenados;
    }

    /**
     * Corta cada texto em {@code textoMaximoPorTrecho} e para no primeiro trecho que faria a soma passar de
     * {@code textoMaximoTotal}: sai inteiro o que tem menor similaridade, nunca um trecho no meio.
     */
    private List<TrechoEncontrado> limitarTexto(List<TrechoEncontrado> ordenados) {
        List<TrechoEncontrado> resultado = new ArrayList<>();
        int soma = 0;
        for (TrechoEncontrado trecho : ordenados) {
            TrechoEncontrado cortado = trecho.comTextoLimitadoA(limites.textoMaximoPorTrecho());
            if (soma + cortado.texto().length() > limites.textoMaximoTotal()) {
                break;
            }
            soma += cortado.texto().length();
            resultado.add(cortado);
        }
        return resultado;
    }

    private static String milhar(int valor) {
        return valor >= 1_000 ? valor / 1_000 + "." + String.format("%03d", valor % 1_000) : String.valueOf(valor);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
