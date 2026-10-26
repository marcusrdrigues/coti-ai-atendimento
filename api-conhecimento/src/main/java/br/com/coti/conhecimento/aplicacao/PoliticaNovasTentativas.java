package br.com.coti.conhecimento.aplicacao;

import br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingTransitoriaException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

/**
 * Novas tentativas com atraso exponencial e variação aleatória, <strong>somente</strong> para
 * {@link FalhaEmbeddingTransitoriaException} (ADR-13: o SDK da OpenAI fica com {@code max-retries: 0}, então este
 * é o único ponto de repetição). Usa o {@code RetryTemplate} do Spring Framework 7. Qualquer outra exceção
 * sobe na primeira ocorrência, sem nova tentativa.
 */
public final class PoliticaNovasTentativas {

    private static final Logger log = LoggerFactory.getLogger(PoliticaNovasTentativas.class);

    private final RetryTemplate modelo;
    private final long novasTentativas;

    public PoliticaNovasTentativas(long novasTentativas, Duration atrasoInicial, double multiplicador,
                                   Duration atrasoMaximo, Duration jitter) {
        this.novasTentativas = novasTentativas;
        this.modelo = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(novasTentativas)
                .delay(atrasoInicial)
                .multiplier(multiplicador)
                .maxDelay(atrasoMaximo)
                .jitter(jitter)
                .includes(FalhaEmbeddingTransitoriaException.class)
                .build());
    }

    public long novasTentativas() {
        return novasTentativas;
    }

    /**
     * Executa a operação; repete só falhas transitórias, até o limite. Esgotadas as tentativas, relança a última
     * falha original (nunca uma exceção de infraestrutura de retry).
     */
    public <T> T executar(Supplier<T> operacao) {
        AtomicInteger tentativa = new AtomicInteger();
        try {
            return modelo.execute(() -> {
                int numero = tentativa.incrementAndGet();
                if (numero > 1) {
                    log.warn("Nova tentativa de embeddings. tentativa={} de {}", numero, novasTentativas + 1);
                }
                return operacao.get();
            });
        } catch (RetryException esgotada) {
            Throwable ultima = esgotada.getLastException();
            if (ultima instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (ultima instanceof Error erro) {
                throw erro;
            }
            throw new IllegalStateException("falha inesperada ao repetir a operação", ultima);
        }
    }
}
