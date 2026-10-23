package br.com.coti.conhecimento.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.coti.conhecimento.aplicacao.porta.AnalisadorConteudoPort;
import br.com.coti.conhecimento.aplicacao.porta.IndexacaoDocumentoPort;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.HashConteudo;
import br.com.coti.conhecimento.dominio.ModeloEmbedding;
import br.com.coti.conhecimento.dominio.NormalizadorTexto;
import br.com.coti.conhecimento.dominio.Pendencia;
import br.com.coti.conhecimento.dominio.ResultadoAnalise;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import br.com.coti.conhecimento.dominio.excecao.ConhecimentoDuplicadoException;
import br.com.coti.conhecimento.dominio.excecao.DadoPessoalDetectadoException;
import br.com.coti.conhecimento.dominio.excecao.DadosInvalidosException;
import br.com.coti.conhecimento.dominio.excecao.IndexacaoFalhouException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CadastrarConhecimentoServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-23T13:00:00Z"), ZoneOffset.UTC);
    private static final String CONTEUDO =
            "O curso Java Fullstack exige conhecimento básico de lógica de programação e inglês técnico.";

    private final RepositorioDocumentoEmMemoria repositorio = new RepositorioDocumentoEmMemoria();
    private final AnalisadorConteudoPort analisador = mock(AnalisadorConteudoPort.class);
    private final IndexacaoDocumentoPort indexacao = mock(IndexacaoDocumentoPort.class);
    private final CadastrarConhecimentoService servico = new CadastrarConhecimentoService(
            repositorio, analisador, indexacao, new ModeloEmbedding("text-embedding-3-small"), RELOGIO);

    @BeforeEach
    void analisadorLimpoPorPadrao() {
        when(analisador.analisar(any())).thenReturn(ResultadoAnalise.limpo());
    }

    private static CadastrarConhecimentoComando comando(String conteudo) {
        return new CadastrarConhecimentoComando("Curso Java", conteudo, "Site COTI, out/2026", Categoria.PRE_REQUISITO,
                LocalDate.of(2027, 6, 30), "gestor-conteudo");
    }

    @Test
    void deveGravarProcessandoIndexarEPublicar() {
        when(indexacao.indexar(any())).thenReturn(4);

        Documento resultado = servico.cadastrar(comando(CONTEUDO));

        assertThat(resultado.status()).isEqualTo(StatusDocumento.PUBLICADO);
        assertThat(resultado.quantidadeChunks()).isEqualTo(4);
        assertThat(resultado.modeloEmbedding()).isEqualTo("text-embedding-3-small");
        assertThat(resultado.cadastradoPor()).isEqualTo("gestor-conteudo");
        assertThat(resultado.cadastradoEm()).isEqualTo(OffsetDateTime.parse("2026-10-23T13:00:00Z"));
        assertThat(resultado.vigenteAte()).isEqualTo(LocalDate.of(2027, 6, 30));
        assertThat(repositorio.documentos.get(resultado.id())).isEqualTo(resultado);
        assertThat(repositorio.inserts).isEqualTo(1);
    }

    @Test
    void deveIndexarODocumentoJaGravadoComoProcessando() {
        when(indexacao.indexar(any())).thenAnswer(invocacao -> {
            Documento recebido = invocacao.getArgument(0);
            assertThat(recebido.status()).isEqualTo(StatusDocumento.PROCESSANDO);
            assertThat(repositorio.documentos).containsKey(recebido.id());
            return 2;
        });

        servico.cadastrar(comando(CONTEUDO));

        verify(indexacao).indexar(any());
    }

    @Test
    void deveGuardarOTextoNormalizadoEOHashDele() {
        when(indexacao.indexar(any())).thenReturn(1);
        String comRuido = "O curso   Java Fullstack exige\r\n\r\n\r\nconhecimento básico​ de lógica de programação.  ";

        Documento resultado = servico.cadastrar(comando(comRuido));

        String esperado = NormalizadorTexto.normalizar(comRuido);
        assertThat(resultado.conteudo()).isEqualTo(esperado).doesNotContain("\r").doesNotContain("​");
        assertThat(resultado.hashConteudo()).isEqualTo(HashConteudo.sha256(esperado));
    }

    @Test
    void deveAnalisarOTextoJaNormalizado() {
        when(indexacao.indexar(any())).thenReturn(1);
        String comRuido = CONTEUDO + "\r\n\r\n\r\n\r\nFim";

        servico.cadastrar(comando(comRuido));

        verify(analisador).analisar(NormalizadorTexto.normalizar(comRuido));
    }

    @Test
    void deveRecusarConteudoCurtoDepoisDeNormalizarSemGravarNada() {
        String quaseVazio = "curto" + " ".repeat(200) + "​".repeat(100);

        assertThatThrownBy(() -> servico.cadastrar(comando(quaseVazio)))
                .isInstanceOfSatisfying(DadosInvalidosException.class, ex -> {
                    assertThat(ex.campos()).extracting(c -> c.campo()).containsExactly("conteudo");
                    assertThat(ex.campos().get(0).mensagem()).doesNotContain("curto");
                });

        assertThat(repositorio.inserts).isZero();
        verify(analisador, never()).analisar(any());
    }

    @Test
    void deveRecusarConteudoAcimaDoLimiteDepoisDeNormalizar() {
        assertThatThrownBy(() -> servico.cadastrar(comando("a".repeat(100_001))))
                .isInstanceOf(DadosInvalidosException.class);
        assertThat(repositorio.inserts).isZero();
    }

    @Test
    void deveRecusarConteudoDuplicadoMesmoComEspacamentoDiferente() {
        when(indexacao.indexar(any())).thenReturn(1);
        servico.cadastrar(comando(CONTEUDO));

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO.replace(" ", "   ") + "\n\n\n")))
                .isInstanceOf(ConhecimentoDuplicadoException.class);

        assertThat(repositorio.inserts).isEqualTo(1);
    }

    @Test
    void naoDeveAnalisarNemIndexarQuandoJaExisteIgual() {
        when(indexacao.indexar(any())).thenReturn(1);
        servico.cadastrar(comando(CONTEUDO));
        org.mockito.Mockito.clearInvocations(analisador, indexacao);

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO))).isInstanceOf(ConhecimentoDuplicadoException.class);

        verify(analisador, never()).analisar(any());
        verify(indexacao, never()).indexar(any());
    }

    @Test
    void deveRecusarDadoPessoalSemGravarNemIndexar() {
        when(analisador.analisar(any())).thenReturn(new ResultadoAnalise(true, List.of()));

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO))).isInstanceOf(DadoPessoalDetectadoException.class);

        assertThat(repositorio.documentos).isEmpty();
        verify(indexacao, never()).indexar(any());
    }

    @Test
    void dadoPessoalTemPrioridadeSobreInjecao() {
        when(analisador.analisar(any())).thenReturn(
                new ResultadoAnalise(true, List.of(Pendencia.POSSIVEL_PROMPT_INJECTION)));

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO))).isInstanceOf(DadoPessoalDetectadoException.class);

        assertThat(repositorio.documentos).isEmpty();
    }

    @Test
    void deveReterEmRevisaoSemIndexarQuandoHaSinalDeInjecao() {
        when(analisador.analisar(any())).thenReturn(
                new ResultadoAnalise(false, List.of(Pendencia.POSSIVEL_PROMPT_INJECTION)));

        Documento resultado = servico.cadastrar(comando(CONTEUDO));

        assertThat(resultado.status()).isEqualTo(StatusDocumento.EM_REVISAO);
        assertThat(resultado.pendencias()).containsExactly(Pendencia.POSSIVEL_PROMPT_INJECTION);
        assertThat(resultado.quantidadeChunks()).isZero();
        assertThat(repositorio.documentos.get(resultado.id())).isEqualTo(resultado);
        verify(indexacao, never()).indexar(any());
    }

    @Test
    void deveMarcarErroProcessamentoELancarQuandoAIndexacaoFalha() {
        RuntimeException causa = new IllegalStateException("embedding indisponível");
        when(indexacao.indexar(any())).thenThrow(causa);

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO)))
                .isInstanceOfSatisfying(IndexacaoFalhouException.class, ex -> {
                    assertThat(ex.getCause()).isSameAs(causa);
                    assertThat(repositorio.documentos.get(ex.conhecimentoId()).status())
                            .isEqualTo(StatusDocumento.ERRO_PROCESSAMENTO);
                    assertThat(repositorio.documentos.get(ex.conhecimentoId()).quantidadeChunks()).isZero();
                });
    }

    @Test
    void indexacaoSemTrechosContaComoFalha() {
        when(indexacao.indexar(any())).thenReturn(0);

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO))).isInstanceOf(IndexacaoFalhouException.class);

        assertThat(repositorio.documentos.values()).singleElement()
                .satisfies(d -> assertThat(d.status()).isEqualTo(StatusDocumento.ERRO_PROCESSAMENTO));
    }

    @Test
    void falhaAoMarcarErroNaoEscondeACausaOriginal() {
        RuntimeException causa = new IllegalStateException("embedding indisponível");
        when(indexacao.indexar(any())).thenAnswer(invocacao -> {
            repositorio.falharAoAtualizar = true;
            throw causa;
        });

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO)))
                .isInstanceOfSatisfying(IndexacaoFalhouException.class, ex -> {
                    assertThat(ex.getCause()).isSameAs(causa);
                    assertThat(causa.getSuppressed()).hasSize(1);
                });
    }
}
