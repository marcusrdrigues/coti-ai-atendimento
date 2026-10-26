package br.com.coti.conhecimento.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingException;
import br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingTransitoriaException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PoliticaNovasTentativasTest {

    private static PoliticaNovasTentativas politica(long novasTentativas) {
        return new PoliticaNovasTentativas(novasTentativas, Duration.ofMillis(1), 2.0, Duration.ofMillis(8),
                Duration.ZERO);
    }

    private static FalhaEmbeddingTransitoriaException transitoria() {
        return new FalhaEmbeddingTransitoriaException("429", null);
    }

    @Test
    void semFalhaExecutaUmaVezEDevolveOResultado() {
        AtomicInteger chamadas = new AtomicInteger();

        String resultado = politica(2).executar(() -> {
            chamadas.incrementAndGet();
            return "ok";
        });

        assertThat(resultado).isEqualTo("ok");
        assertThat(chamadas).hasValue(1);
    }

    @Test
    void repeteFalhaTransitoriaAteDarCerto() {
        AtomicInteger chamadas = new AtomicInteger();

        String resultado = politica(2).executar(() -> {
            if (chamadas.incrementAndGet() < 3) {
                throw transitoria();
            }
            return "ok na terceira";
        });

        assertThat(resultado).isEqualTo("ok na terceira");
        assertThat(chamadas).hasValue(3);
    }

    @Test
    void esgotadasAsTentativasRelancaAUltimaFalhaOriginal() {
        AtomicInteger chamadas = new AtomicInteger();
        FalhaEmbeddingTransitoriaException ultima = transitoria();

        assertThatThrownBy(() -> politica(2).executar(() -> {
            if (chamadas.incrementAndGet() == 3) {
                throw ultima;
            }
            throw transitoria();
        })).isSameAs(ultima);

        assertThat(chamadas).as("1 chamada + 2 novas tentativas").hasValue(3);
    }

    @Test
    void falhaDefinitivaNaoEhRepetida() {
        AtomicInteger chamadas = new AtomicInteger();
        FalhaEmbeddingException definitiva = new FalhaEmbeddingException("401", null);

        assertThatThrownBy(() -> politica(2).executar(() -> {
            chamadas.incrementAndGet();
            throw definitiva;
        })).isSameAs(definitiva);

        assertThat(chamadas).hasValue(1);
    }

    @Test
    void outraExcecaoQualquerNaoEhRepetida() {
        AtomicInteger chamadas = new AtomicInteger();
        IllegalStateException erro = new IllegalStateException("bug");

        assertThatThrownBy(() -> politica(2).executar(() -> {
            chamadas.incrementAndGet();
            throw erro;
        })).isSameAs(erro);

        assertThat(chamadas).hasValue(1);
    }

    @Test
    void zeroNovasTentativasExecutaUmaVezSo() {
        AtomicInteger chamadas = new AtomicInteger();

        assertThatThrownBy(() -> politica(0).executar(() -> {
            chamadas.incrementAndGet();
            throw transitoria();
        })).isInstanceOf(FalhaEmbeddingTransitoriaException.class);

        assertThat(chamadas).hasValue(1);
    }

    @Test
    void oAtrasoCresceDeFormaExponencialLimitadoAoMaximo() {
        PoliticaNovasTentativas comAtraso = new PoliticaNovasTentativas(4, Duration.ofMillis(20), 2.0,
                Duration.ofMillis(60), Duration.ZERO);
        List<Long> instantes = new ArrayList<>();

        assertThatThrownBy(() -> comAtraso.executar(() -> {
            instantes.add(System.nanoTime());
            throw transitoria();
        })).isInstanceOf(FalhaEmbeddingTransitoriaException.class);

        List<Long> intervalosMs = new ArrayList<>();
        for (int i = 1; i < instantes.size(); i++) {
            intervalosMs.add((instantes.get(i) - instantes.get(i - 1)) / 1_000_000);
        }
        assertThat(intervalosMs).hasSize(4);
        // 20, 40, 60 (limitado), 60 (limitado): tolerância para o escalonador do sistema operacional
        assertThat(intervalosMs.get(0)).isBetween(15L, 200L);
        assertThat(intervalosMs.get(1)).isGreaterThanOrEqualTo(intervalosMs.get(0));
        assertThat(intervalosMs.get(3)).isLessThan(250L);
    }

    @Test
    void ajitterNaoPassaDoLimiteConfigurado() {
        PoliticaNovasTentativas comJitter = new PoliticaNovasTentativas(2, Duration.ofMillis(5), 1.0,
                Duration.ofMillis(100), Duration.ofMillis(10));
        AtomicInteger chamadas = new AtomicInteger();

        assertThatThrownBy(() -> comJitter.executar(() -> {
            chamadas.incrementAndGet();
            throw transitoria();
        })).isInstanceOf(FalhaEmbeddingTransitoriaException.class);

        assertThat(chamadas).hasValue(3);
        assertThat(comJitter.novasTentativas()).isEqualTo(2);
    }
}
