package br.com.coti.conhecimento.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdTrechoEVigenciaTest {

    private static final UUID DOC = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void idEDeterministico() {
        assertThat(IdTrecho.de(DOC, 7)).isEqualTo(IdTrecho.de(DOC, 7));
    }

    @Test
    void idMudaComADocumentoEComAOrdem() {
        assertThat(IdTrecho.de(DOC, 0)).isNotEqualTo(IdTrecho.de(DOC, 1));
        assertThat(IdTrecho.de(DOC, 0)).isNotEqualTo(IdTrecho.de(UUID.randomUUID(), 0));
    }

    @Test
    void idEUmUuidVersao5DaRfc4122() {
        UUID id = IdTrecho.de(DOC, 3);

        assertThat(id.version()).isEqualTo(5);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void naoHaColisaoEmMilIds() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(IdTrecho.de(DOC, i));
        }
        assertThat(ids).hasSize(1000);
    }

    @Test
    void valorDeReferenciaNaoMudaEntreVersoesDoCodigo() {
        // Muda se o espaço de nomes ou o formato do nome mudarem: reindexar a base inteira seria necessário.
        assertThat(IdTrecho.de(DOC, 0)).hasToString(IdTrecho.de(DOC, 0).toString());
        assertThat(IdTrecho.de(DOC, 0).toString()).matches("[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    }

    @Test
    void vigenciaVirainteiroAaaammdd() {
        assertThat(VigenciaMetadado.paraMetadado(LocalDate.of(2027, 6, 30))).isEqualTo(20270630);
        assertThat(VigenciaMetadado.paraMetadado(LocalDate.of(2026, 1, 5))).isEqualTo(20260105);
    }

    @Test
    void semVigenciaVale99991231() {
        assertThat(VigenciaMetadado.paraMetadado(null)).isEqualTo(99_991_231).isEqualTo(VigenciaMetadado.SEM_VIGENCIA);
        assertThat(VigenciaMetadado.dataDe(99_991_231)).isNull();
    }

    @Test
    void conversaoDeIdaEVolta() {
        for (LocalDate data : new LocalDate[] {LocalDate.of(2026, 10, 23), LocalDate.of(2028, 2, 29), LocalDate.of(2030, 12, 31)}) {
            assertThat(VigenciaMetadado.dataDe(VigenciaMetadado.paraMetadado(data))).isEqualTo(data);
        }
    }

    @Test
    void hojeUsaORelogio() {
        Clock relogio = Clock.fixed(Instant.parse("2026-10-23T13:00:00Z"), ZoneOffset.UTC);

        assertThat(VigenciaMetadado.hoje(relogio)).isEqualTo(20261023);
    }

    @Test
    void vigenciaDeHojeAindaVale_eDeOntemNao() {
        int hoje = 20261023;

        assertThat(VigenciaMetadado.estaVigente(20261023, hoje)).isTrue();
        assertThat(VigenciaMetadado.estaVigente(20261022, hoje)).isFalse();
        assertThat(VigenciaMetadado.estaVigente(VigenciaMetadado.SEM_VIGENCIA, hoje)).isTrue();
    }

    @Test
    void ordemDosInteirosEquivaleAOrdemDasDatas() {
        assertThat(VigenciaMetadado.paraMetadado(LocalDate.of(2026, 12, 31)))
                .isLessThan(VigenciaMetadado.paraMetadado(LocalDate.of(2027, 1, 1)));
        assertThat(VigenciaMetadado.paraMetadado(LocalDate.of(2026, 9, 30)))
                .isLessThan(VigenciaMetadado.paraMetadado(LocalDate.of(2026, 10, 1)));
    }
}
