package br.com.coti.atendimentos.dominio;

import br.com.coti.atendimentos.dominio.excecao.TempoEsgotadoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Orçamento de tempo de um atendimento (30 s). É passado por todo o fluxo: cada etapa usa no máximo o tempo que
 * resta e nenhuma nova tentativa começa sem tempo.
 */
public final class Prazo {

    private final Clock relogio;
    private final Instant limite;

    private Prazo(Clock relogio, Instant limite) {
        this.relogio = relogio;
        this.limite = limite;
    }

    public static Prazo iniciar(Clock relogio, Duration total) {
        if (total.isZero() || total.isNegative()) {
            throw new IllegalArgumentException("o prazo total deve ser positivo");
        }
        return new Prazo(relogio, relogio.instant().plus(total));
    }

    /** Tempo que resta; nunca negativo. */
    public Duration restante() {
        Duration restante = Duration.between(relogio.instant(), limite);
        return restante.isNegative() ? Duration.ZERO : restante;
    }

    public boolean expirado() {
        return !relogio.instant().isBefore(limite);
    }

    /** @throws TempoEsgotadoException se o prazo já acabou */
    public void exigirTempo() {
        if (expirado()) {
            throw new TempoEsgotadoException();
        }
    }

    /** O menor entre o tempo limite da etapa e o que resta do prazo. */
    public Duration limitar(Duration tempoDaEtapa) {
        Duration restante = restante();
        return tempoDaEtapa.compareTo(restante) <= 0 ? tempoDaEtapa : restante;
    }
}
