package br.com.coti.conhecimento.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NormalizadorTextoTest {

    @Test
    void padronizaQuebrasDeLinhaParaLF() {
        assertThat(NormalizadorTexto.normalizar("a\r\nb\rc d e")).isEqualTo("a\nb\nc\nd\ne");
    }

    @Test
    void removeCaracteresDeControleMasPreservaQuebraETabulacaoComoEspaco() {
        assertThat(NormalizadorTexto.normalizar("a\u0000b\u0007c\u001Bd\te\nf")).isEqualTo("abcd e\nf");
    }

    @Test
    void removeCaracteresInvisiveisQuePoderiamEsconderInstrucoes() {
        // espaço de largura zero, marca de ordem de bytes, controle bidirecional e hífen opcional
        assertThat(NormalizadorTexto.normalizar("ig​nore﻿ ‮x‬­y")).isEqualTo("ignore xy");
    }

    @Test
    void reduzEspacosRepetidosENaoQuebravel() {
        assertThat(NormalizadorTexto.normalizar("a   b  c d")).isEqualTo("a b c d");
    }

    @Test
    void removeEspacosAoRedorDasQuebrasDeLinha() {
        assertThat(NormalizadorTexto.normalizar("linha 1   \n   linha 2")).isEqualTo("linha 1\nlinha 2");
    }

    @Test
    void mantemNoMaximoUmaLinhaEmBrancoEntreParagrafos() {
        assertThat(NormalizadorTexto.normalizar("p1\n\n\n\n\np2\n\np3")).isEqualTo("p1\n\np2\n\np3");
    }

    @Test
    void removeEspacosNasPontas() {
        assertThat(NormalizadorTexto.normalizar("\n\n  texto  \n\n")).isEqualTo("texto");
    }

    @Test
    void normalizaUnicodeParaNFC() {
        assertThat(NormalizadorTexto.normalizar("é")).isEqualTo("é");
    }

    @Test
    void preservaAcentosEMarkdown() {
        String markdown = "## Pré-requisitos\n\n- Lógica de programação\n- **Inglês** técnico";

        assertThat(NormalizadorTexto.normalizar(markdown)).isEqualTo(markdown);
    }

    @Test
    void nuloViraTextoVazio() {
        assertThat(NormalizadorTexto.normalizar(null)).isEmpty();
    }

    @Test
    void textosEquivalentesTemOMesmoHash() {
        String a = "Curso de Java\r\n\r\n\r\nCarga horária:   80 h  ";
        String b = "Curso de Java\n\nCarga horária: 80 h";

        assertThat(HashConteudo.sha256(NormalizadorTexto.normalizar(a)))
                .isEqualTo(HashConteudo.sha256(NormalizadorTexto.normalizar(b)));
    }

    @Test
    void textosDiferentesTemHashesDiferentes() {
        assertThat(HashConteudo.sha256(NormalizadorTexto.normalizar("Curso de Java")))
                .isNotEqualTo(HashConteudo.sha256(NormalizadorTexto.normalizar("Curso de Angular")));
    }

    @Test
    void hashEUmSha256EmHexadecimalMinusculo() {
        assertThat(HashConteudo.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(HashConteudo.sha256("qualquer texto")).hasSize(64).matches("[0-9a-f]{64}");
    }
}
