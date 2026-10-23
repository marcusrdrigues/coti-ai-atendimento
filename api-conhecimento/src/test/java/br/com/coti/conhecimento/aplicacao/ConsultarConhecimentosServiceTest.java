package br.com.coti.conhecimento.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.DocumentoResumo;
import br.com.coti.conhecimento.dominio.FiltroDocumentos;
import br.com.coti.conhecimento.dominio.HashConteudo;
import br.com.coti.conhecimento.dominio.Pagina;
import br.com.coti.conhecimento.dominio.Pendencia;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import br.com.coti.conhecimento.dominio.excecao.ConhecimentoNaoEncontradoException;
import br.com.coti.conhecimento.dominio.excecao.DadosInvalidosException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConsultarConhecimentosServiceTest {

    private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-10-23T10:00:00Z");

    private final RepositorioDocumentoEmMemoria repositorio = new RepositorioDocumentoEmMemoria();
    private final ConsultarConhecimentosService servico = new ConsultarConhecimentosService(repositorio);

    private Documento adicionar(String chave, Categoria categoria, StatusDocumento status, int minutos) {
        String hash = HashConteudo.sha256(chave);
        OffsetDateTime quando = BASE.plusMinutes(minutos);
        Documento documento = switch (status) {
            case EM_REVISAO -> Documento.emRevisao(UUID.randomUUID(), "t-" + chave, "conteúdo " + chave, "f",
                    categoria, hash, "m", null, "g", quando, List.of(Pendencia.POSSIVEL_PROMPT_INJECTION));
            case PUBLICADO -> Documento.emProcessamento(UUID.randomUUID(), "t-" + chave, "conteúdo " + chave, "f",
                    categoria, hash, "m", null, "g", quando).publicar(2);
            case ERRO_PROCESSAMENTO -> Documento.emProcessamento(UUID.randomUUID(), "t-" + chave, "conteúdo " + chave,
                    "f", categoria, hash, "m", null, "g", quando).falharProcessamento();
            case PROCESSANDO -> Documento.emProcessamento(UUID.randomUUID(), "t-" + chave, "conteúdo " + chave, "f",
                    categoria, hash, "m", null, "g", quando);
        };
        repositorio.inserir(documento);
        return documento;
    }

    @Test
    void deveListarDoMaisRecenteParaOMaisAntigo() {
        adicionar("a", Categoria.CURSO, StatusDocumento.PUBLICADO, 1);
        adicionar("b", Categoria.CURSO, StatusDocumento.PUBLICADO, 3);
        adicionar("c", Categoria.CURSO, StatusDocumento.PUBLICADO, 2);

        Pagina<DocumentoResumo> pagina = servico.listar(FiltroDocumentos.todos(), 0, 20);

        assertThat(pagina.itens()).extracting(DocumentoResumo::titulo).containsExactly("t-b", "t-c", "t-a");
        assertThat(pagina.totalItens()).isEqualTo(3);
        assertThat(pagina.totalPaginas()).isEqualTo(1);
    }

    @Test
    void naoDeveListarDocumentoEmProcessamento() {
        adicionar("a", Categoria.CURSO, StatusDocumento.PUBLICADO, 1);
        adicionar("b", Categoria.CURSO, StatusDocumento.PROCESSANDO, 2);

        assertThat(servico.listar(FiltroDocumentos.todos(), 0, 20).itens())
                .extracting(DocumentoResumo::titulo).containsExactly("t-a");
    }

    @Test
    void deveFiltrarPorStatusECategoria() {
        adicionar("a", Categoria.CURSO, StatusDocumento.PUBLICADO, 1);
        adicionar("b", Categoria.MODALIDADE, StatusDocumento.PUBLICADO, 2);
        adicionar("c", Categoria.CURSO, StatusDocumento.EM_REVISAO, 3);

        assertThat(servico.listar(new FiltroDocumentos(StatusDocumento.EM_REVISAO, null), 0, 20).itens())
                .extracting(DocumentoResumo::titulo).containsExactly("t-c");
        assertThat(servico.listar(new FiltroDocumentos(null, Categoria.MODALIDADE), 0, 20).itens())
                .extracting(DocumentoResumo::titulo).containsExactly("t-b");
        assertThat(servico.listar(new FiltroDocumentos(StatusDocumento.PUBLICADO, Categoria.CURSO), 0, 20).itens())
                .extracting(DocumentoResumo::titulo).containsExactly("t-a");
    }

    @Test
    void devePaginar() {
        for (int i = 0; i < 5; i++) {
            adicionar("d" + i, Categoria.CURSO, StatusDocumento.PUBLICADO, i);
        }

        Pagina<DocumentoResumo> segunda = servico.listar(FiltroDocumentos.todos(), 1, 2);

        assertThat(segunda.itens()).hasSize(2);
        assertThat(segunda.pagina()).isEqualTo(1);
        assertThat(segunda.totalItens()).isEqualTo(5);
        assertThat(segunda.totalPaginas()).isEqualTo(3);
        assertThat(servico.listar(FiltroDocumentos.todos(), 9, 2).itens()).isEmpty();
    }

    @Test
    void naoDevePermitirFiltrarPorProcessando() {
        assertThatThrownBy(() -> servico.listar(new FiltroDocumentos(StatusDocumento.PROCESSANDO, null), 0, 20))
                .isInstanceOfSatisfying(DadosInvalidosException.class,
                        ex -> assertThat(ex.campos().get(0).campo()).isEqualTo("status"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101, 1000})
    void deveRecusarTamanhoForaDoIntervalo(int tamanho) {
        assertThatThrownBy(() -> servico.listar(FiltroDocumentos.todos(), 0, tamanho))
                .isInstanceOfSatisfying(DadosInvalidosException.class,
                        ex -> assertThat(ex.campos().get(0).campo()).isEqualTo("tamanho"));
    }

    @Test
    void deveRecusarPaginaNegativa() {
        assertThatThrownBy(() -> servico.listar(FiltroDocumentos.todos(), -1, 20))
                .isInstanceOfSatisfying(DadosInvalidosException.class,
                        ex -> assertThat(ex.campos().get(0).campo()).isEqualTo("pagina"));
    }

    @Test
    void deveAceitarOsLimitesDoTamanho() {
        assertThat(servico.listar(FiltroDocumentos.todos(), 0, 1).tamanho()).isEqualTo(1);
        assertThat(servico.listar(FiltroDocumentos.todos(), 0, 100).tamanho()).isEqualTo(100);
    }

    @Test
    void deveDetalharComConteudo() {
        Documento emRevisao = adicionar("x", Categoria.CURSO, StatusDocumento.EM_REVISAO, 1);

        Documento detalhe = servico.detalhar(emRevisao.id());

        assertThat(detalhe.conteudo()).isEqualTo("conteúdo x");
        assertThat(detalhe.pendencias()).containsExactly(Pendencia.POSSIVEL_PROMPT_INJECTION);
    }

    @Test
    void naoDeveDetalharDocumentoInexistente() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> servico.detalhar(id)).isInstanceOfSatisfying(
                ConhecimentoNaoEncontradoException.class, ex -> assertThat(ex.id()).isEqualTo(id));
    }

    @Test
    void naoDeveDetalharDocumentoEmProcessamento() {
        Documento processando = adicionar("p", Categoria.CURSO, StatusDocumento.PROCESSANDO, 1);

        assertThatThrownBy(() -> servico.detalhar(processando.id()))
                .isInstanceOf(ConhecimentoNaoEncontradoException.class);
    }

    @Test
    void deveDetalharErroDeProcessamento() {
        Documento erro = adicionar("e", Categoria.CURSO, StatusDocumento.ERRO_PROCESSAMENTO, 1);

        assertThat(servico.detalhar(erro.id()).status()).isEqualTo(StatusDocumento.ERRO_PROCESSAMENTO);
    }
}
