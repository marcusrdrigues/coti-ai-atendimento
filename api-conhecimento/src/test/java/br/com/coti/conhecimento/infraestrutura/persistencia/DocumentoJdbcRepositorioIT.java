package br.com.coti.conhecimento.infraestrutura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.conhecimento.aplicacao.porta.RepositorioDocumentoPort;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.DocumentoResumo;
import br.com.coti.conhecimento.dominio.FiltroDocumentos;
import br.com.coti.conhecimento.dominio.HashConteudo;
import br.com.coti.conhecimento.dominio.Pagina;
import br.com.coti.conhecimento.dominio.Pendencia;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import br.com.coti.conhecimento.dominio.excecao.ConhecimentoDuplicadoException;
import br.com.coti.conhecimento.suporte.TesteWeb;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@TesteWeb
class DocumentoJdbcRepositorioIT {

    private static final OffsetDateTime BASE =
            OffsetDateTime.of(2026, 10, 23, 10, 0, 0, 123_456_000, ZoneOffset.ofHours(-3));

    @Autowired
    private RepositorioDocumentoPort repositorio;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void limpar() {
        jdbc.sql("DELETE FROM documento").update();
    }

    private static Documento processando(String chave, Categoria categoria, int minutos) {
        return Documento.emProcessamento(UUID.randomUUID(), "Título " + chave, "Conteúdo com acentuação " + chave,
                "Fonte " + chave, categoria, HashConteudo.sha256(chave), "text-embedding-3-small", null, "gestor",
                BASE.plusMinutes(minutos));
    }

    private static Documento emRevisao(String chave, int minutos) {
        return Documento.emRevisao(UUID.randomUUID(), "Título " + chave, "Conteúdo " + chave, "Fonte",
                Categoria.INSTITUCIONAL, HashConteudo.sha256(chave), "text-embedding-3-small",
                LocalDate.of(2027, 6, 30), "gestor", BASE.plusMinutes(minutos),
                List.of(Pendencia.POSSIVEL_PROMPT_INJECTION));
    }

    @Test
    void deveGravarELerTodosOsCamposDeUmDocumentoProcessando() {
        Documento original = processando("a", Categoria.CURSO, 0);

        repositorio.inserir(original);
        Documento lido = repositorio.buscarPorId(original.id()).orElseThrow();

        assertThat(lido).usingRecursiveComparison()
                .withEqualsForType((a, b) -> a.toInstant().equals(b.toInstant()), OffsetDateTime.class)
                .isEqualTo(original);
        assertThat(lido.vigenteAte()).isNull();
        assertThat(lido.pendencias()).isEmpty();
    }

    @Test
    void deveGravarELerPendenciasEVigencia() {
        Documento original = emRevisao("b", 0);

        repositorio.inserir(original);
        Documento lido = repositorio.buscarPorId(original.id()).orElseThrow();

        assertThat(lido.status()).isEqualTo(StatusDocumento.EM_REVISAO);
        assertThat(lido.pendencias()).containsExactly(Pendencia.POSSIVEL_PROMPT_INJECTION);
        assertThat(lido.vigenteAte()).isEqualTo(LocalDate.of(2027, 6, 30));
        assertThat(lido.cadastradoEm().toInstant()).isEqualTo(original.cadastradoEm().toInstant());
    }

    @Test
    void devePreservarTextoComAcentosEMarkdown() {
        Documento original = Documento.emProcessamento(UUID.randomUUID(), "Programação — Java", "## Pré-requisitos\n\n- Lógica\n- Inglês técnico: café, ação",
                "Manual do aluno", Categoria.PRE_REQUISITO, HashConteudo.sha256("md"), "m", null, "g", BASE);

        repositorio.inserir(original);

        Documento lido = repositorio.buscarPorId(original.id()).orElseThrow();
        assertThat(lido.titulo()).isEqualTo("Programação — Java");
        assertThat(lido.conteudo()).isEqualTo(original.conteudo());
    }

    @Test
    void deveBuscarPorHash() {
        Documento original = processando("c", Categoria.CURSO, 0);
        repositorio.inserir(original);

        assertThat(repositorio.buscarPorHash(original.hashConteudo())).get().extracting(Documento::id)
                .isEqualTo(original.id());
        assertThat(repositorio.buscarPorHash(HashConteudo.sha256("inexistente"))).isEmpty();
    }

    @Test
    void deveRecusarHashDuplicadoComExcecaoDeDominio() {
        repositorio.inserir(processando("d", Categoria.CURSO, 0));
        Documento outro = Documento.emProcessamento(UUID.randomUUID(), "Outro título", "Outro conteúdo", "f",
                Categoria.PROGRAMA, HashConteudo.sha256("d"), "m", null, "g", BASE);

        assertThatThrownBy(() -> repositorio.inserir(outro)).isInstanceOf(ConhecimentoDuplicadoException.class);

        assertThat(repositorio.buscarPorId(outro.id())).isEmpty();
    }

    @Test
    void deveDevolverVazioParaIdInexistente() {
        assertThat(repositorio.buscarPorId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void devePublicarAtualizandoStatusEQuantidade() {
        Documento original = processando("e", Categoria.CURSO, 0);
        repositorio.inserir(original);

        repositorio.atualizarEstado(original.publicar(5));

        Documento lido = repositorio.buscarPorId(original.id()).orElseThrow();
        assertThat(lido.status()).isEqualTo(StatusDocumento.PUBLICADO);
        assertThat(lido.quantidadeChunks()).isEqualTo(5);
    }

    @Test
    void deveMarcarErroDeProcessamento() {
        Documento original = processando("f", Categoria.CURSO, 0);
        repositorio.inserir(original);

        repositorio.atualizarEstado(original.falharProcessamento());

        assertThat(repositorio.buscarPorId(original.id()).orElseThrow().status())
                .isEqualTo(StatusDocumento.ERRO_PROCESSAMENTO);
    }

    @Test
    void naoDeveAtualizarDocumentoQueNaoExiste() {
        assertThatIllegalStateException().isThrownBy(() -> repositorio.atualizarEstado(processando("g", Categoria.CURSO, 0).publicar(1)));
    }

    @Test
    void deveListarDoMaisRecenteParaOMaisAntigoSemOConteudo() {
        Documento antigo = processando("h1", Categoria.CURSO, 1);
        Documento novo = processando("h2", Categoria.CURSO, 3);
        Documento meio = processando("h3", Categoria.CURSO, 2);
        List.of(antigo, novo, meio).forEach(d -> {
            repositorio.inserir(d);
            repositorio.atualizarEstado(d.publicar(1));
        });

        Pagina<DocumentoResumo> pagina = repositorio.listar(FiltroDocumentos.todos(), 0, 20);

        assertThat(pagina.itens()).extracting(DocumentoResumo::id).containsExactly(novo.id(), meio.id(), antigo.id());
        assertThat(pagina.totalItens()).isEqualTo(3);
        assertThat(pagina.itens().get(0).quantidadeChunks()).isEqualTo(1);
        assertThat(pagina.itens().get(0).cadastradoEm().toInstant())
                .isEqualTo(novo.cadastradoEm().truncatedTo(ChronoUnit.MICROS).toInstant());
    }

    @Test
    void naoDeveListarDocumentosEmProcessamento() {
        Documento emAndamento = processando("i1", Categoria.CURSO, 1);
        repositorio.inserir(emAndamento);
        repositorio.inserir(emRevisao("i2", 2));

        Pagina<DocumentoResumo> pagina = repositorio.listar(FiltroDocumentos.todos(), 0, 20);

        assertThat(pagina.itens()).extracting(DocumentoResumo::titulo).containsExactly("Título i2");
        assertThat(pagina.totalItens()).isEqualTo(1);
    }

    @Test
    void deveFiltrarPorStatusECategoriaEContarComOsMesmosFiltros() {
        Documento curso = processando("j1", Categoria.CURSO, 1);
        Documento modalidade = processando("j2", Categoria.MODALIDADE, 2);
        repositorio.inserir(curso);
        repositorio.atualizarEstado(curso.publicar(1));
        repositorio.inserir(modalidade);
        repositorio.atualizarEstado(modalidade.falharProcessamento());
        repositorio.inserir(emRevisao("j3", 3));

        assertThat(repositorio.listar(new FiltroDocumentos(StatusDocumento.PUBLICADO, null), 0, 20).itens())
                .extracting(DocumentoResumo::id).containsExactly(curso.id());
        assertThat(repositorio.listar(new FiltroDocumentos(null, Categoria.MODALIDADE), 0, 20).totalItens())
                .isEqualTo(1);
        assertThat(repositorio.listar(new FiltroDocumentos(StatusDocumento.EM_REVISAO, Categoria.CURSO), 0, 20).itens())
                .isEmpty();
        assertThat(repositorio.listar(new FiltroDocumentos(StatusDocumento.EM_REVISAO, null), 0, 20).totalItens())
                .isEqualTo(1);
    }

    @Test
    void devePaginarComTotalIndependenteDaPagina() {
        for (int i = 0; i < 5; i++) {
            repositorio.inserir(emRevisao("k" + i, i));
        }

        Pagina<DocumentoResumo> primeira = repositorio.listar(FiltroDocumentos.todos(), 0, 2);
        Pagina<DocumentoResumo> terceira = repositorio.listar(FiltroDocumentos.todos(), 2, 2);
        Pagina<DocumentoResumo> alemDoFim = repositorio.listar(FiltroDocumentos.todos(), 5, 2);

        assertThat(primeira.itens()).hasSize(2);
        assertThat(terceira.itens()).hasSize(1);
        assertThat(alemDoFim.itens()).isEmpty();
        assertThat(List.of(primeira.totalItens(), terceira.totalItens(), alemDoFim.totalItens())).containsOnly(5L);
        assertThat(primeira.totalPaginas()).isEqualTo(3);
    }

    @Test
    void ordemEstavelQuandoOsTimestampsSaoIguais() {
        for (int i = 0; i < 4; i++) {
            repositorio.inserir(emRevisao("l" + i, 0));
        }

        List<UUID> primeiraLeitura = repositorio.listar(FiltroDocumentos.todos(), 0, 20).itens().stream()
                .map(DocumentoResumo::id).toList();
        List<UUID> segundaLeitura = repositorio.listar(FiltroDocumentos.todos(), 0, 20).itens().stream()
                .map(DocumentoResumo::id).toList();

        assertThat(primeiraLeitura).containsExactlyElementsOf(segundaLeitura);
    }

    @Test
    void parametrosNaoSaoInterpretadosComoSql() {
        Documento hostil = Documento.emProcessamento(UUID.randomUUID(), "x'; DROP TABLE documento; --",
                "conteúdo ' OR '1'='1", "fonte'); DELETE FROM documento; --", Categoria.CURSO,
                HashConteudo.sha256("hostil"), "m", null, "g'--", BASE);

        repositorio.inserir(hostil);

        Documento lido = repositorio.buscarPorId(hostil.id()).orElseThrow();
        assertThat(lido.titulo()).isEqualTo("x'; DROP TABLE documento; --");
        assertThat(lido.cadastradoPor()).isEqualTo("g'--");
    }
}
