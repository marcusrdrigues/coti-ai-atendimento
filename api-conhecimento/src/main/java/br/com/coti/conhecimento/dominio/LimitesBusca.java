package br.com.coti.conhecimento.dominio;

import java.time.Duration;

/**
 * Regras numéricas da busca semântica (RF04, RNF04, RNF05): tamanho da pergunta, quantidade de trechos, limiar de
 * similaridade, cortes de texto e tempo máximo da geração do embedding da pergunta.
 */
public record LimitesBusca(
        int perguntaMinima,
        int perguntaMaxima,
        int limitePadrao,
        int limiteMaximo,
        double limiarSimilaridade,
        int textoMaximoPorTrecho,
        int textoMaximoTotal,
        Duration tempoLimite) {

    public LimitesBusca {
        if (perguntaMinima < 1 || perguntaMaxima < perguntaMinima) {
            throw new IllegalArgumentException("tamanho da pergunta inválido: 1 <= mínimo <= máximo");
        }
        if (limitePadrao < 1 || limiteMaximo < limitePadrao) {
            throw new IllegalArgumentException("limite de trechos inválido: 1 <= padrão <= máximo");
        }
        if (limiarSimilaridade < 0 || limiarSimilaridade > 1) {
            throw new IllegalArgumentException("limiar de similaridade deve estar entre 0 e 1");
        }
        if (textoMaximoPorTrecho < 1 || textoMaximoTotal < textoMaximoPorTrecho) {
            throw new IllegalArgumentException("cortes de texto inválidos: 1 <= por trecho <= total");
        }
        if (tempoLimite == null || tempoLimite.isZero() || tempoLimite.isNegative()) {
            throw new IllegalArgumentException("tempo limite da busca deve ser positivo");
        }
    }

    /** Valores do contrato: pergunta de 3 a 1.000, 5 trechos (até 10), limiar 0,70, cortes 2.000 / 8.000, 5 s. */
    public static LimitesBusca padrao() {
        return new LimitesBusca(3, 1_000, 5, 10, 0.70, 2_000, 8_000, Duration.ofSeconds(5));
    }
}
