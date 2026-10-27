package br.com.coti.conhecimento.infraestrutura.vetorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.coti.conhecimento.aplicacao.porta.BuscaVetorialPort.Consulta;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.TrechoEncontrado;
import br.com.coti.conhecimento.dominio.excecao.BaseConhecimentoIndisponivelException;
import br.com.coti.conhecimento.dominio.excecao.ModeloIndisponivelException;
import br.com.coti.conhecimento.suporte.EmbeddingModelFalso;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;

class PgVectorBuscaAdapterTest {

    private static final Consulta CONSULTA =
            new Consulta("pergunta válida", 4, 0.70, "text-embedding-3-small", LocalDate.of(2026, 10, 27));

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final PgVectorBuscaAdapter adaptador = new PgVectorBuscaAdapter(vectorStore);

    private static Document documento(UUID id, UUID documentoId, Double score) {
        return Document.builder().id(id.toString()).text("texto do trecho")
                .metadata(Map.of("documentoId", documentoId.toString(), "titulo", "Título", "fonte", "Fonte",
                        "categoria", "PRE_REQUISITO", "ordem", 2, "status", "PUBLICADO",
                        "modeloEmbedding", "text-embedding-3-small", "vigenteAte", 99991231))
                .score(score).build();
    }

    @Test
    void montaARequisicaoComPerguntaTopKELimiar() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        adaptador.buscar(CONSULTA);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest requisicao = captor.getValue();
        assertThat(requisicao.getQuery()).isEqualTo("pergunta válida");
        assertThat(requisicao.getTopK()).isEqualTo(4);
        assertThat(requisicao.getSimilarityThreshold()).isEqualTo(0.70);
        assertThat(requisicao.hasFilterExpression()).isTrue();
    }

    @Test
    void ofiltroCobrePublicacaoModeloEVigencia() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        adaptador.buscar(CONSULTA);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        String filtro = captor.getValue().getFilterExpression().toString();
        assertThat(filtro).contains("status", "PUBLICADO", "modeloEmbedding", "text-embedding-3-small",
                "vigenteAte", "20261027");
    }

    @Test
    void perguntaComAspasEOperadoresNaoMudaOFiltro() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        Consulta maliciosa = new Consulta("') OR status == 'EM_REVISAO", 5, 0.70, "text-embedding-3-small",
                LocalDate.of(2026, 10, 27));

        adaptador.buscar(maliciosa);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression().toString()).doesNotContain("EM_REVISAO");
    }

    @Test
    void convertePeloContratoSemMetadadosInternos() {
        UUID id = UUID.randomUUID();
        UUID documentoId = UUID.randomUUID();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(documento(id, documentoId, 0.8312)));

        List<TrechoEncontrado> resultado = adaptador.buscar(CONSULTA);

        assertThat(resultado).containsExactly(new TrechoEncontrado(id, "texto do trecho", documentoId, "Título",
                "Fonte", Categoria.PRE_REQUISITO, 0.8312));
    }

    @Test
    void scoreAcimaDeUmPorArredondamentoVira1ESemScoreFicaNulo() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                documento(UUID.randomUUID(), UUID.randomUUID(), 1.0000002),
                documento(UUID.randomUUID(), UUID.randomUUID(), null)));

        List<TrechoEncontrado> resultado = adaptador.buscar(CONSULTA);

        assertThat(resultado).extracting(TrechoEncontrado::similaridade).containsExactly(1.0, null);
    }

    @Test
    void falhaDoBancoViraBaseIndisponivel() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new DataAccessResourceFailureException("conexão perdida"));

        assertThatThrownBy(() -> adaptador.buscar(CONSULTA)).isInstanceOf(BaseConhecimentoIndisponivelException.class);
    }

    @Test
    void falhaTransitoriaDoBancoTambemViraBaseIndisponivel() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new TransientDataAccessResourceException("tempo esgotado"));

        assertThatThrownBy(() -> adaptador.buscar(CONSULTA)).isInstanceOf(BaseConhecimentoIndisponivelException.class);
    }

    @Test
    void falhasDoProvedorDeEmbeddingViramModeloIndisponivel() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(EmbeddingModelFalso.limiteDeRequisicoes())
                .thenThrow(EmbeddingModelFalso.erroDoServidor())
                .thenThrow(EmbeddingModelFalso.credencialRecusada())
                .thenThrow(EmbeddingModelFalso.tempoEsgotado());

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> adaptador.buscar(CONSULTA)).isInstanceOf(ModeloIndisponivelException.class);
        }
    }

    @Test
    void mensagemDaFalhaNaoCarregaAPergunta() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(EmbeddingModelFalso.credencialRecusada());

        assertThatThrownBy(() -> adaptador.buscar(CONSULTA))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("pergunta válida"));
    }

    @Test
    void falhaDeOutraNaturezaNaoEhEscondida() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> adaptador.buscar(CONSULTA)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void trechoSemMetadadoObrigatorioEhErroInternoEExplicito() {
        Document semFonte = Document.builder().id(UUID.randomUUID().toString()).text("x")
                .metadata(Map.of("documentoId", UUID.randomUUID().toString(), "titulo", "T", "categoria", "CURSO"))
                .score(0.9).build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(semFonte));

        assertThatThrownBy(() -> adaptador.buscar(CONSULTA)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fonte");
    }
}
