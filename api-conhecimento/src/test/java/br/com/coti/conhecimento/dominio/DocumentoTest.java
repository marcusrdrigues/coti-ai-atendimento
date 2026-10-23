package br.com.coti.conhecimento.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DocumentoTest {

    private static final String HASH = HashConteudo.sha256("conteudo");
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-10-23T10:00:00-03:00");

    private static Documento processando() {
        return Documento.emProcessamento(UUID.randomUUID(), "Título", "Conteúdo do documento", "Fonte",
                Categoria.CURSO, HASH, "modelo", LocalDate.of(2027, 1, 31), "gestor", AGORA);
    }

    private static Documento emRevisao() {
        return Documento.emRevisao(UUID.randomUUID(), "Título", "Conteúdo do documento", "Fonte",
                Categoria.CURSO, HASH, "modelo", null, "gestor", AGORA, List.of(Pendencia.POSSIVEL_PROMPT_INJECTION));
    }

    @Test
    void documentoNovoNaoTemTrechosNemPendencias() {
        Documento documento = processando();

        assertThat(documento.status()).isEqualTo(StatusDocumento.PROCESSANDO);
        assertThat(documento.quantidadeChunks()).isZero();
        assertThat(documento.pendencias()).isEmpty();
    }

    @Test
    void documentoEmRevisaoTemPendenciaESemTrechos() {
        Documento documento = emRevisao();

        assertThat(documento.status()).isEqualTo(StatusDocumento.EM_REVISAO);
        assertThat(documento.pendencias()).containsExactly(Pendencia.POSSIVEL_PROMPT_INJECTION);
        assertThat(documento.quantidadeChunks()).isZero();
    }

    @Test
    void publicarGuardaAQuantidadeDeTrechos() {
        Documento publicado = processando().publicar(4);

        assertThat(publicado.status()).isEqualTo(StatusDocumento.PUBLICADO);
        assertThat(publicado.quantidadeChunks()).isEqualTo(4);
    }

    @Test
    void publicarComZeroTrechosViolaAInvariante() {
        assertThatIllegalArgumentException().isThrownBy(() -> processando().publicar(0))
                .withMessageContaining("PUBLICADO");
    }

    @Test
    void falharProcessamentoZeraOsTrechos() {
        Documento erro = processando().falharProcessamento();

        assertThat(erro.status()).isEqualTo(StatusDocumento.ERRO_PROCESSAMENTO);
        assertThat(erro.quantidadeChunks()).isZero();
    }

    @Test
    void soProcessandoPodeMudarDeEstado() {
        Documento publicado = processando().publicar(2);
        Documento erro = processando().falharProcessamento();

        assertThatIllegalStateException().isThrownBy(() -> publicado.publicar(3));
        assertThatIllegalStateException().isThrownBy(publicado::falharProcessamento);
        assertThatIllegalStateException().isThrownBy(() -> emRevisao().publicar(1));
        assertThatIllegalStateException().isThrownBy(() -> erro.publicar(1));
    }

    @ParameterizedTest
    @EnumSource(value = StatusDocumento.class, names = {"PUBLICADO", "EM_REVISAO", "ERRO_PROCESSAMENTO"})
    void estadosFinaisNaoTemDestino(StatusDocumento final_) {
        assertThat(final_.destinosPermitidos()).isEmpty();
    }

    @Test
    void processandoSoVaiParaPublicadoOuErro() {
        assertThat(StatusDocumento.PROCESSANDO.destinosPermitidos())
                .containsExactlyInAnyOrder(StatusDocumento.PUBLICADO, StatusDocumento.ERRO_PROCESSAMENTO);
        assertThat(StatusDocumento.PROCESSANDO.podeIrPara(StatusDocumento.EM_REVISAO)).isFalse();
    }

    @Test
    void processandoNaoEVisivelAoGestor() {
        assertThat(StatusDocumento.PROCESSANDO.visivelAoGestor()).isFalse();
        assertThat(StatusDocumento.PUBLICADO.visivelAoGestor()).isTrue();
        assertThat(StatusDocumento.EM_REVISAO.visivelAoGestor()).isTrue();
        assertThat(StatusDocumento.ERRO_PROCESSAMENTO.visivelAoGestor()).isTrue();
    }

    @Test
    void camposObrigatoriosNaoAceitamVazioNemNulo() {
        UUID id = UUID.randomUUID();

        assertThatIllegalArgumentException().isThrownBy(() -> Documento.emProcessamento(id, " ", "c", "f",
                Categoria.CURSO, HASH, "m", null, "g", AGORA)).withMessageContaining("titulo");
        assertThatIllegalArgumentException().isThrownBy(() -> Documento.emProcessamento(id, "t", "", "f",
                Categoria.CURSO, HASH, "m", null, "g", AGORA)).withMessageContaining("conteudo");
        assertThatIllegalArgumentException().isThrownBy(() -> Documento.emProcessamento(id, "t", "c", null,
                Categoria.CURSO, HASH, "m", null, "g", AGORA)).withMessageContaining("fonte");
        assertThatIllegalArgumentException().isThrownBy(() -> Documento.emProcessamento(id, "t", "c", "f",
                Categoria.CURSO, HASH, "m", null, " ", AGORA)).withMessageContaining("cadastradoPor");
        assertThatNullPointerException().isThrownBy(() -> Documento.emProcessamento(null, "t", "c", "f",
                Categoria.CURSO, HASH, "m", null, "g", AGORA));
        assertThatNullPointerException().isThrownBy(() -> Documento.emProcessamento(id, "t", "c", "f",
                null, HASH, "m", null, "g", AGORA));
    }

    @Test
    void hashPrecisaSerSha256EmHexadecimalMinusculo() {
        UUID id = UUID.randomUUID();

        assertThatIllegalArgumentException().isThrownBy(() -> Documento.emProcessamento(id, "t", "c", "f",
                Categoria.CURSO, "curto", "m", null, "g", AGORA));
        assertThatIllegalArgumentException().isThrownBy(() -> Documento.emProcessamento(id, "t", "c", "f",
                Categoria.CURSO, HASH.toUpperCase(), "m", null, "g", AGORA));
    }

    @Test
    void soEmRevisaoTemPendencias() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Documento(UUID.randomUUID(), "t", "c", "f",
                Categoria.CURSO, StatusDocumento.PROCESSANDO, List.of(Pendencia.POSSIVEL_PROMPT_INJECTION), HASH,
                0, "m", null, "g", AGORA)).withMessageContaining("EM_REVISAO");
        assertThatIllegalArgumentException().isThrownBy(() -> new Documento(UUID.randomUUID(), "t", "c", "f",
                Categoria.CURSO, StatusDocumento.EM_REVISAO, List.of(), HASH, 0, "m", null, "g", AGORA));
    }

    @Test
    void soPublicadoTemTrechos() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Documento(UUID.randomUUID(), "t", "c", "f",
                Categoria.CURSO, StatusDocumento.EM_REVISAO, List.of(Pendencia.POSSIVEL_PROMPT_INJECTION), HASH,
                2, "m", null, "g", AGORA));
        assertThatIllegalArgumentException().isThrownBy(() -> new Documento(UUID.randomUUID(), "t", "c", "f",
                Categoria.CURSO, StatusDocumento.PROCESSANDO, null, HASH, -1, "m", null, "g", AGORA));
    }

    @Test
    void listaDePendenciasEImutavel() {
        Documento documento = emRevisao();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> documento.pendencias().add(Pendencia.POSSIVEL_PROMPT_INJECTION))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resumoNaoTrazOConteudo() {
        DocumentoResumo resumo = processando().publicar(2).resumo();

        assertThat(resumo.titulo()).isEqualTo("Título");
        assertThat(resumo.status()).isEqualTo(StatusDocumento.PUBLICADO);
        assertThat(resumo.quantidadeChunks()).isEqualTo(2);
        assertThat(DocumentoResumo.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName).doesNotContain("conteudo");
    }

    @Test
    void paginaCalculaTotalDePaginas() {
        assertThat(new Pagina<>(List.of(), 0, 20, 0).totalPaginas()).isZero();
        assertThat(new Pagina<>(List.of(), 0, 20, 20).totalPaginas()).isEqualTo(1);
        assertThat(new Pagina<>(List.of(), 0, 20, 21).totalPaginas()).isEqualTo(2);
        assertThatIllegalArgumentException().isThrownBy(() -> new Pagina<>(List.of(), 0, 0, 0));
    }
}
