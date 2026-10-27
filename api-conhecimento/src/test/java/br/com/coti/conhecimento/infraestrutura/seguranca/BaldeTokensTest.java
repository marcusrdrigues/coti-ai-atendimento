package br.com.coti.conhecimento.infraestrutura.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BaldeTokensTest {

    /** Relógio que anda só quando o teste manda. */
    private static final class RelogioManual extends Clock {
        private Instant agora = Instant.parse("2026-10-27T12:00:00Z");

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final RelogioManual relogio = new RelogioManual();
    private final BaldeTokens balde = new BaldeTokens(10, Duration.ofMinutes(1), relogio, 1_000);

    @Test
    void permiteAteACapacidadeERecusaAPartirDaProxima() {
        for (int i = 0; i < 10; i++) {
            assertThat(balde.tentar("1.1.1.1").permitido()).as("requisição %d", i + 1).isTrue();
        }

        BaldeTokens.Resultado recusada = balde.tentar("1.1.1.1");

        assertThat(recusada.permitido()).isFalse();
        assertThat(recusada.segundosParaTentar()).isEqualTo(6); // 10 por minuto: uma ficha a cada 6 s
    }

    @Test
    void cadaChaveTemOSeuBalde() {
        for (int i = 0; i < 10; i++) {
            balde.tentar("1.1.1.1");
        }

        assertThat(balde.tentar("1.1.1.1").permitido()).isFalse();
        assertThat(balde.tentar("2.2.2.2").permitido()).isTrue();
    }

    @Test
    void asFichasVoltamComOTempo() {
        for (int i = 0; i < 10; i++) {
            balde.tentar("a");
        }
        assertThat(balde.tentar("a").permitido()).isFalse();

        relogio.avancar(Duration.ofSeconds(6));
        assertThat(balde.tentar("a").permitido()).isTrue();
        assertThat(balde.tentar("a").permitido()).isFalse();
    }

    @Test
    void oRetryAfterDiminuiConformeOTempoPassa() {
        for (int i = 0; i < 10; i++) {
            balde.tentar("a");
        }

        relogio.avancar(Duration.ofSeconds(4));

        assertThat(balde.tentar("a").segundosParaTentar()).isEqualTo(2);
    }

    @Test
    void ObaldeNuncaPassaDaCapacidadeMesmoDepoisDeMuitoTempo() {
        balde.tentar("a");
        relogio.avancar(Duration.ofHours(5));

        int permitidas = 0;
        while (balde.tentar("a").permitido()) {
            permitidas++;
        }

        assertThat(permitidas).isEqualTo(10);
    }

    @Test
    void umaRequisicaoPorMinutoRecusaAsegundaERecomendaSessentaSegundos() {
        BaldeTokens umPorMinuto = new BaldeTokens(1, Duration.ofMinutes(1), relogio, 10);

        assertThat(umPorMinuto.tentar("a").permitido()).isTrue();
        BaldeTokens.Resultado recusada = umPorMinuto.tentar("a");

        assertThat(recusada.permitido()).isFalse();
        assertThat(recusada.segundosParaTentar()).isEqualTo(60);
    }

    @Test
    void oNumeroDeChavesRastreadasEhLimitadoEAMenosUsadaEEsquecida() {
        BaldeTokens pequeno = new BaldeTokens(1, Duration.ofMinutes(1), relogio, 3);
        pequeno.tentar("a");
        pequeno.tentar("b");
        pequeno.tentar("c");
        pequeno.tentar("a"); // "a" fica recente; "b" é a menos usada

        pequeno.tentar("d");

        assertThat(pequeno.chavesRastreadas()).isEqualTo(3);
        assertThat(pequeno.tentar("b").permitido()).as("b foi esquecida e voltou com o balde cheio").isTrue();
        assertThat(pequeno.tentar("a").permitido()).as("a continuava rastreada e sem ficha").isFalse();
    }

    @Test
    void parametrosInvalidosSaoRecusados() {
        assertThatThrownBy(() -> new BaldeTokens(0, Duration.ofMinutes(1), relogio, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BaldeTokens(1, Duration.ZERO, relogio, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BaldeTokens(1, Duration.ofMinutes(1), relogio, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
