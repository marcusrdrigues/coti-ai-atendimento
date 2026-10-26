package br.com.coti.conhecimento.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.coti.conhecimento.aplicacao.porta.RepositorioVetorialPort;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.HashConteudo;
import br.com.coti.conhecimento.dominio.IdTrecho;
import br.com.coti.conhecimento.dominio.Trecho;
import br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingException;
import br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingTransitoriaException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IndexarDocumentoServiceTest {

    private final RepositorioVetorialPort vetorial = mock(RepositorioVetorialPort.class);
    private final PoliticaNovasTentativas politica = new PoliticaNovasTentativas(2, Duration.ofMillis(1), 2.0,
            Duration.ofMillis(4), Duration.ZERO);
    private final Documento documento = Documento.emProcessamento(UUID.randomUUID(), "Título", "Conteúdo", "Fonte",
            Categoria.CURSO, HashConteudo.sha256("x"), "modelo", null, "gestor", OffsetDateTime.now());

    private List<Trecho> trechos(int quantidade) {
        return IntStream.range(0, quantidade)
                .mapToObj(i -> new Trecho(IdTrecho.de(documento.id(), i), i, "texto " + i)).toList();
    }

    @Test
    void divideEmLotesDoTamanhoConfiguradoPreservandoAOrdem() {
        IndexarDocumentoService servico = new IndexarDocumentoService(vetorial, politica, 100);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Trecho>> lotes = ArgumentCaptor.forClass(List.class);

        servico.indexar(documento, trechos(250));

        verify(vetorial, times(3)).gravarLote(any(), lotes.capture());
        assertThat(lotes.getAllValues()).extracting(List::size).containsExactly(100, 100, 50);
        List<Integer> ordens = new ArrayList<>();
        lotes.getAllValues().forEach(l -> l.forEach(t -> ordens.add(t.ordem())));
        assertThat(ordens).containsExactlyElementsOf(IntStream.range(0, 250).boxed().toList());
    }

    @Test
    void umLoteSoQuandoCabeEm100() {
        new IndexarDocumentoService(vetorial, politica, 100).indexar(documento, trechos(100));

        verify(vetorial, times(1)).gravarLote(any(), any());
    }

    @Test
    void semTrechosNaoChamaOBanco() {
        new IndexarDocumentoService(vetorial, politica, 100).indexar(documento, List.of());

        verify(vetorial, never()).gravarLote(any(), any());
    }

    @Test
    void repeteSoOLoteQueFalhouTransitoriamente() {
        IndexarDocumentoService servico = new IndexarDocumentoService(vetorial, politica, 2);
        int[] chamadas = {0};
        org.mockito.Mockito.doAnswer(invocacao -> {
            chamadas[0]++;
            if (chamadas[0] == 2) {
                throw new FalhaEmbeddingTransitoriaException("429", null);
            }
            return null;
        }).when(vetorial).gravarLote(any(), any());

        servico.indexar(documento, trechos(4));

        // lote 1 (ok), lote 2 (falha, repete e passa) => 3 chamadas
        verify(vetorial, times(3)).gravarLote(any(), any());
    }

    @Test
    void paraNoPrimeiroLoteComFalhaDefinitivaESemProsseguir() {
        IndexarDocumentoService servico = new IndexarDocumentoService(vetorial, politica, 2);
        FalhaEmbeddingException definitiva = new FalhaEmbeddingException("401", null);
        doThrow(definitiva).when(vetorial).gravarLote(any(), any());

        assertThatThrownBy(() -> servico.indexar(documento, trechos(6))).isSameAs(definitiva);

        verify(vetorial, times(1)).gravarLote(any(), any());
    }

    @Test
    void esgotaAsNovasTentativasEPropagaAFalhaTransitoria() {
        IndexarDocumentoService servico = new IndexarDocumentoService(vetorial, politica, 2);
        doThrow(new FalhaEmbeddingTransitoriaException("429", null)).when(vetorial).gravarLote(any(), any());

        assertThatThrownBy(() -> servico.indexar(documento, trechos(4)))
                .isInstanceOf(FalhaEmbeddingTransitoriaException.class);

        verify(vetorial, times(3)).gravarLote(any(), any());
    }

    @Test
    void tamanhoDeLoteInvalidoEhRecusado() {
        assertThatIllegalArgumentException().isThrownBy(() -> new IndexarDocumentoService(vetorial, politica, 0));
    }
}
