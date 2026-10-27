package br.com.coti.conhecimento.infraestrutura.seguranca;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Limite de requisições por chave (IP ou credencial) pelo algoritmo do balde de fichas, em memória (ADR-12). O
 * balde começa cheio com {@code capacidade} fichas e recebe fichas de forma contínua, {@code capacidade} por
 * {@code janela}. Cada requisição gasta uma ficha; sem ficha, é recusada e informa em quantos segundos haverá uma.
 *
 * <p>Vale para uma instância só: com várias instâncias o limite passa a ser por instância, e valer no conjunto
 * exige armazenamento compartilhado (tarefa de pré-produção). O número de chaves rastreadas é limitado: ao
 * encher, a menos usada recentemente é esquecida e volta a ter o balde cheio.
 */
public final class BaldeTokens {

    /** Resultado de uma tentativa; {@code segundosParaTentar} é 0 quando permitida. */
    public record Resultado(boolean permitido, long segundosParaTentar) {
    }

    private static final class Balde {
        private double fichas;
        private long ultimaNanos;

        Balde(double fichas, long ultimaNanos) {
            this.fichas = fichas;
            this.ultimaNanos = ultimaNanos;
        }
    }

    private final int capacidade;
    private final double fichasPorNano;
    private final Clock relogio;
    private final Map<String, Balde> baldes;

    public BaldeTokens(int capacidade, Duration janela, Clock relogio, int maximoDeChaves) {
        if (capacidade < 1 || janela.isZero() || janela.isNegative() || maximoDeChaves < 1) {
            throw new IllegalArgumentException("capacidade, janela e máximo de chaves devem ser positivos");
        }
        this.capacidade = capacidade;
        this.fichasPorNano = (double) capacidade / janela.toNanos();
        this.relogio = relogio;
        this.baldes = new LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Balde> maisAntigo) {
                return size() > maximoDeChaves;
            }
        };
    }

    public synchronized Resultado tentar(String chave) {
        long agora = nanos();
        Balde balde = baldes.computeIfAbsent(chave, k -> new Balde(capacidade, agora));
        balde.fichas = Math.min(capacidade, balde.fichas + (agora - balde.ultimaNanos) * fichasPorNano);
        balde.ultimaNanos = agora;
        if (balde.fichas >= 1.0) {
            balde.fichas -= 1.0;
            return new Resultado(true, 0);
        }
        // A tolerância evita que o erro de ponto flutuante transforme 2,0000000001 s em 3 s.
        long segundos = (long) Math.ceil((1.0 - balde.fichas) / fichasPorNano / 1_000_000_000.0 - 1e-6);
        return new Resultado(false, Math.max(1, segundos));
    }

    /** Esquece todas as chaves (volta tudo a balde cheio); usado pelos testes. */
    public synchronized void limpar() {
        baldes.clear();
    }

    public synchronized int chavesRastreadas() {
        return baldes.size();
    }

    private long nanos() {
        java.time.Instant instante = relogio.instant();
        return instante.getEpochSecond() * 1_000_000_000L + instante.getNano();
    }
}
