package br.com.coti.atendimentos.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.atendimentos.dominio.excecao.TempoEsgotadoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PrazoTest {

    private static final Instant INICIO = Instant.parse("2026-10-27T12:00:00Z");

    /** Relógio que anda só quando o teste manda. */
    private static final class RelogioManual extends Clock {
        private Instant agora = INICIO;

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final RelogioManual relogio = new RelogioManual();

    @Test
    void comecaComOTempoTotal() {
        Prazo prazo = Prazo.iniciar(relogio, Duration.ofSeconds(30));

        assertThat(prazo.restante()).isEqualTo(Duration.ofSeconds(30));
        assertThat(prazo.expirado()).isFalse();
    }

    @Test
    void oTempoRestanteDiminuiComORelogio() {
        Prazo prazo = Prazo.iniciar(relogio, Duration.ofSeconds(30));

        relogio.avancar(Duration.ofSeconds(12));

        assertThat(prazo.restante()).isEqualTo(Duration.ofSeconds(18));
    }

    @Test
    void expiraExatamenteNoLimite() {
        Prazo prazo = Prazo.iniciar(relogio, Duration.ofSeconds(30));

        relogio.avancar(Duration.ofSeconds(30).minusMillis(1));
        assertThat(prazo.expirado()).isFalse();
        relogio.avancar(Duration.ofMillis(1));
        assertThat(prazo.expirado()).isTrue();
        assertThat(prazo.restante()).isEqualTo(Duration.ZERO);
    }

    @Test
    void oTempoRestanteNuncaEhNegativo() {
        Prazo prazo = Prazo.iniciar(relogio, Duration.ofSeconds(1));

        relogio.avancar(Duration.ofMinutes(5));

        assertThat(prazo.restante()).isEqualTo(Duration.ZERO);
    }

    @Test
    void exigirTempoLancaQuandoExpirado() {
        Prazo prazo = Prazo.iniciar(relogio, Duration.ofSeconds(2));

        assertThatCode(prazo::exigirTempo).doesNotThrowAnyException();
        relogio.avancar(Duration.ofSeconds(2));
        assertThatThrownBy(prazo::exigirTempo).isInstanceOf(TempoEsgotadoException.class);
    }

    @Test
    void limitarUsaOMenorEntreAEtapaEOQueResta() {
        Prazo prazo = Prazo.iniciar(relogio, Duration.ofSeconds(30));

        assertThat(prazo.limitar(Duration.ofSeconds(5))).isEqualTo(Duration.ofSeconds(5));
        relogio.avancar(Duration.ofSeconds(27));
        assertThat(prazo.limitar(Duration.ofSeconds(5))).isEqualTo(Duration.ofSeconds(3));
        relogio.avancar(Duration.ofSeconds(10));
        assertThat(prazo.limitar(Duration.ofSeconds(5))).isEqualTo(Duration.ZERO);
    }

    @Test
    void prazoTotalPrecisaSerPositivo() {
        assertThatThrownBy(() -> Prazo.iniciar(relogio, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Prazo.iniciar(relogio, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
