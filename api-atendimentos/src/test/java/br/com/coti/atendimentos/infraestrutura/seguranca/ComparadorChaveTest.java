package br.com.coti.atendimentos.infraestrutura.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ComparadorChaveTest {

    @Test
    void deveAceitarChaveIgual() {
        assertThat(ComparadorChave.iguais("segredo", "segredo")).isTrue();
    }

    @Test
    void deveRecusarChaveDiferenteOuComTamanhoDiferente() {
        assertThat(ComparadorChave.iguais("segredo", "segredO")).isFalse();
        assertThat(ComparadorChave.iguais("segredo-maior", "segredo")).isFalse();
    }

    @Test
    void deveRecusarChaveRecebidaAusente() {
        assertThat(ComparadorChave.iguais(null, "segredo")).isFalse();
    }

    @Test
    void naoDeveAceitarChaveEsperadaEmBrancoMesmoComRecebidaEmBranco() {
        assertThat(ComparadorChave.iguais("", "")).isFalse();
        assertThat(ComparadorChave.iguais("x", " ")).isFalse();
        assertThat(ComparadorChave.iguais("x", null)).isFalse();
    }
}
