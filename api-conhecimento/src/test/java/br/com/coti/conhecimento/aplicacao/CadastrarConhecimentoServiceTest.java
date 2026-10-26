package br.com.coti.conhecimento.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.coti.conhecimento.aplicacao.porta.AnalisadorConteudoPort;
import br.com.coti.conhecimento.aplicacao.porta.RepositorioVetorialPort;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.ConfiguracaoDivisao;
import br.com.coti.conhecimento.dominio.DivisorTexto;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.HashConteudo;
import br.com.coti.conhecimento.dominio.ModeloEmbedding;
import br.com.coti.conhecimento.dominio.NormalizadorTexto;
import br.com.coti.conhecimento.dominio.Pendencia;
import br.com.coti.conhecimento.dominio.ResultadoAnalise;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import br.com.coti.conhecimento.dominio.Trecho;
import br.com.coti.conhecimento.dominio.excecao.ConhecimentoDuplicadoException;
import br.com.coti.conhecimento.dominio.excecao.DadoPessoalDetectadoException;
import br.com.coti.conhecimento.dominio.excecao.DadosInvalidosException;
import br.com.coti.conhecimento.dominio.excecao.DocumentoGrandeDemaisException;
import br.com.coti.conhecimento.dominio.excecao.IndexacaoFalhouException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class CadastrarConhecimentoServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-23T13:00:00Z"), ZoneOffset.UTC);
    private static final String CONTEUDO =
            "O curso Java Fullstack exige conhecimento básico de lógica de programação e inglês técnico.";

    private final RepositorioDocumentoEmMemoria repositorio = new RepositorioDocumentoEmMemoria();
    private final RepositorioVetorialPort vetorial = mock(RepositorioVetorialPort.class);
    private final AnalisadorConteudoPort analisador = mock(AnalisadorConteudoPort.class);
    private final IndexarDocumentoService indexacao = mock(IndexarDocumentoService.class);
    private final PublicarDocumentoService publicacao = mock(PublicarDocumentoService.class);
    private final CadastrarConhecimentoService servico = new CadastrarConhecimentoService(
            repositorio, vetorial, analisador, new DivisorTexto(ConfiguracaoDivisao.padrao()), indexacao,
            publicacao, new ModeloEmbedding("text-embedding-3-small"), RELOGIO);

    @BeforeEach
    void preparar() {
        when(analisador.analisar(any())).thenReturn(ResultadoAnalise.limpo());
        when(publicacao.publicar(any(), anyInt())).thenAnswer(invocacao ->
                ((Documento) invocacao.getArgument(0)).publicar(invocacao.getArgument(1)));
    }

    private static CadastrarConhecimentoComando comando(String conteudo) {
        return new CadastrarConhecimentoComando("Curso Java", conteudo, "Site COTI, out/2026", Categoria.PRE_REQUISITO,
                LocalDate.of(2027, 6, 30), "gestor-conteudo");
    }

    // ---- caminho feliz

    @Test
    void deveGravarProcessandoIndexarEPublicarNessaOrdem() {
        Documento resultado = servico.cadastrar(comando(CONTEUDO));

        InOrder ordem = inOrder(analisador, indexacao, publicacao);
        ordem.verify(analisador).analisar(any());
        ordem.verify(indexacao).indexar(any(), any());
        ordem.verify(publicacao).publicar(any(), eq(1));
        assertThat(resultado.status()).isEqualTo(StatusDocumento.PUBLICADO);
        assertThat(resultado.quantidadeChunks()).isEqualTo(1);
        assertThat(resultado.modeloEmbedding()).isEqualTo("text-embedding-3-small");
        assertThat(resultado.cadastradoPor()).isEqualTo("gestor-conteudo");
        assertThat(resultado.cadastradoEm()).isEqualTo(OffsetDateTime.parse("2026-10-23T13:00:00Z"));
        assertThat(resultado.vigenteAte()).isEqualTo(LocalDate.of(2027, 6, 30));
        assertThat(repositorio.inserts).isEqualTo(1);
    }

    @Test
    void deveIndexarODocumentoJaGravadoComoProcessandoComOsTrechosDoDivisor() {
        String longo = "Esta é uma frase longa sobre o curso de Java. ".repeat(100);
        ArgumentCaptor<Documento> documento = ArgumentCaptor.forClass(Documento.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Trecho>> trechos = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.doAnswer(invocacao -> {
            Documento recebido = invocacao.getArgument(0);
            assertThat(recebido.status()).isEqualTo(StatusDocumento.PROCESSANDO);
            assertThat(repositorio.documentos).containsKey(recebido.id());
            return null;
        }).when(indexacao).indexar(any(), any());

        servico.cadastrar(comando(longo));

        verify(indexacao).indexar(documento.capture(), trechos.capture());
        assertThat(trechos.getValue()).hasSizeGreaterThan(2);
        assertThat(trechos.getValue()).extracting(Trecho::ordem).startsWith(0, 1, 2);
        verify(publicacao).publicar(documento.getValue(), trechos.getValue().size());
    }

    @Test
    void deveGuardarOTextoNormalizadoEOHashDele() {
        String comRuido = "O curso   Java Fullstack exige\r\n\r\n\r\nconhecimento básico​ de lógica de programação.  ";

        servico.cadastrar(comando(comRuido));

        String esperado = NormalizadorTexto.normalizar(comRuido);
        assertThat(repositorio.documentos.values()).singleElement().satisfies(d -> {
            assertThat(d.conteudo()).isEqualTo(esperado).doesNotContain("\r").doesNotContain("​");
            assertThat(d.hashConteudo()).isEqualTo(HashConteudo.sha256(esperado));
        });
    }

    @Test
    void deveAnalisarOTextoJaNormalizado() {
        String comRuido = CONTEUDO + "\r\n\r\n\r\n\r\nFim";

        servico.cadastrar(comando(comRuido));

        verify(analisador).analisar(NormalizadorTexto.normalizar(comRuido));
    }

    // ---- rejeições antes de gravar

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
        servico.cadastrar(comando(CONTEUDO));

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO.replace(" ", "   ") + "\n\n\n")))
                .isInstanceOf(ConhecimentoDuplicadoException.class);

        assertThat(repositorio.inserts).isEqualTo(1);
    }

    @Test
    void naoDeveAnalisarNemIndexarQuandoJaExisteIgual() {
        servico.cadastrar(comando(CONTEUDO));
        org.mockito.Mockito.clearInvocations(analisador, indexacao, publicacao);

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO))).isInstanceOf(ConhecimentoDuplicadoException.class);

        verify(analisador, never()).analisar(any());
        verify(indexacao, never()).indexar(any(), any());
    }

    @Test
    void deveRecusarDadoPessoalSemGravarNemIndexar() {
        when(analisador.analisar(any())).thenReturn(new ResultadoAnalise(true, List.of()));

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO))).isInstanceOf(DadoPessoalDetectadoException.class);

        assertThat(repositorio.documentos).isEmpty();
        verify(indexacao, never()).indexar(any(), any());
    }

    @Test
    void dadoPessoalTemPrioridadeSobreInjecao() {
        when(analisador.analisar(any())).thenReturn(
                new ResultadoAnalise(true, List.of(Pendencia.POSSIVEL_PROMPT_INJECTION)));

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO))).isInstanceOf(DadoPessoalDetectadoException.class);

        assertThat(repositorio.documentos).isEmpty();
    }

    @Test
    void deveReterEmRevisaoSemDividirNemIndexarQuandoHaSinalDeInjecao() {
        when(analisador.analisar(any())).thenReturn(
                new ResultadoAnalise(false, List.of(Pendencia.POSSIVEL_PROMPT_INJECTION)));

        Documento resultado = servico.cadastrar(comando(CONTEUDO));

        assertThat(resultado.status()).isEqualTo(StatusDocumento.EM_REVISAO);
        assertThat(resultado.pendencias()).containsExactly(Pendencia.POSSIVEL_PROMPT_INJECTION);
        assertThat(resultado.quantidadeChunks()).isZero();
        assertThat(repositorio.documentos.get(resultado.id())).isEqualTo(resultado);
        verify(indexacao, never()).indexar(any(), any());
        verify(publicacao, never()).publicar(any(), anyInt());
    }

    @Test
    void deveRecusarDocumentoGrandeDemaisAntesDeGravarQualquerCoisa() {
        CadastrarConhecimentoService comLimiteBaixo = new CadastrarConhecimentoService(repositorio, vetorial,
                analisador, new DivisorTexto(new ConfiguracaoDivisao(100, 20, 10, 2)), indexacao, publicacao,
                new ModeloEmbedding("m"), RELOGIO);
        String texto = "Frase de teste sobre o curso de Java Fullstack. ".repeat(20);

        assertThatThrownBy(() -> comLimiteBaixo.cadastrar(comando(texto)))
                .isInstanceOf(DocumentoGrandeDemaisException.class);

        assertThat(repositorio.documentos).isEmpty();
        assertThat(repositorio.inserts).isZero();
        verify(indexacao, never()).indexar(any(), any());
    }

    // ---- falhas na indexação e compensação

    @Test
    void deveApagarOsTrechosEMarcarErroQuandoAIndexacaoFalha() {
        RuntimeException causa = new IllegalStateException("embedding indisponível");
        doThrow(causa).when(indexacao).indexar(any(), any());

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO)))
                .isInstanceOfSatisfying(IndexacaoFalhouException.class, ex -> {
                    assertThat(ex.getCause()).isSameAs(causa);
                    Documento gravado = repositorio.documentos.get(ex.conhecimentoId());
                    assertThat(gravado.status()).isEqualTo(StatusDocumento.ERRO_PROCESSAMENTO);
                    assertThat(gravado.quantidadeChunks()).isZero();
                    verify(vetorial).removerPorDocumento(ex.conhecimentoId());
                });
        verify(publicacao, never()).publicar(any(), anyInt());
    }

    @Test
    void deveCompensarTambemQuandoAPublicacaoFalha() {
        RuntimeException causa = new IllegalStateException("falha ao publicar");
        doThrow(causa).when(publicacao).publicar(any(), anyInt());

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO)))
                .isInstanceOfSatisfying(IndexacaoFalhouException.class, ex -> {
                    assertThat(ex.getCause()).isSameAs(causa);
                    assertThat(repositorio.documentos.get(ex.conhecimentoId()).status())
                            .isEqualTo(StatusDocumento.ERRO_PROCESSAMENTO);
                    verify(vetorial).removerPorDocumento(ex.conhecimentoId());
                });
    }

    @Test
    void falhaAoApagarOsTrechosNaoImpedeMarcarErroNemEscondeACausa() {
        RuntimeException causa = new IllegalStateException("embedding indisponível");
        doThrow(causa).when(indexacao).indexar(any(), any());
        doThrow(new IllegalStateException("banco caiu")).when(vetorial).removerPorDocumento(any());

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO)))
                .isInstanceOfSatisfying(IndexacaoFalhouException.class, ex -> {
                    assertThat(ex.getCause()).isSameAs(causa);
                    assertThat(causa.getSuppressed()).hasSize(1);
                    assertThat(repositorio.documentos.get(ex.conhecimentoId()).status())
                            .isEqualTo(StatusDocumento.ERRO_PROCESSAMENTO);
                });
    }

    @Test
    void falhaAoMarcarErroNaoEscondeACausaOriginal() {
        RuntimeException causa = new IllegalStateException("embedding indisponível");
        org.mockito.Mockito.doAnswer(invocacao -> {
            repositorio.falharAoAtualizar = true;
            throw causa;
        }).when(indexacao).indexar(any(), any());

        assertThatThrownBy(() -> servico.cadastrar(comando(CONTEUDO)))
                .isInstanceOfSatisfying(IndexacaoFalhouException.class, ex -> {
                    assertThat(ex.getCause()).isSameAs(causa);
                    assertThat(causa.getSuppressed()).hasSize(1);
                });
    }
}
