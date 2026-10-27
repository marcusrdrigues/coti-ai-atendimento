package br.com.coti.conhecimento.infraestrutura.vetorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.HashConteudo;
import br.com.coti.conhecimento.dominio.IdTrecho;
import br.com.coti.conhecimento.dominio.Trecho;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * O que o modelo de embedding recebe de cada trecho é só o texto. O {@code OpenAiEmbeddingModel} usa
 * {@code Document.getFormattedContent(modo)}, que por padrão acrescenta os metadados (ids, status, datas) e um
 * prefixo de quebras de linha ao texto; isso polui o vetor e enfraquece a busca. Achado pelo teste entre os dois
 * serviços (T053), porque o dublê de embeddings dos outros testes lê direto {@code getText()} e escondia o problema.
 */
class PgVectorRepositorioAdapterFormatoTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final PgVectorRepositorioAdapter adaptador = new PgVectorRepositorioAdapter(vectorStore, mock(JdbcClient.class));

    private List<org.springframework.ai.document.Document> gravar(String texto) {
        Documento documento = Documento.emProcessamento(UUID.randomUUID(), "Título secreto do metadado", "Conteúdo",
                "Fonte secreta do metadado", Categoria.CURSO, HashConteudo.sha256("x"), "text-embedding-3-small",
                LocalDate.of(2027, 6, 30), "gestor", OffsetDateTime.parse("2026-10-27T10:00:00-03:00"));
        adaptador.gravarLote(documento, List.of(new Trecho(IdTrecho.de(documento.id(), 0), 0, texto)));
        ArgumentCaptor<List<org.springframework.ai.document.Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        return captor.getValue();
    }

    @ParameterizedTest
    @EnumSource(MetadataMode.class)
    void emQualquerModoDeMetadadosOConteudoParaEmbeddingEOTextoPuro(MetadataMode modo) {
        var documento = gravar("Para o curso Java é recomendado conhecimento básico de lógica.").get(0);

        assertThat(documento.getFormattedContent(modo))
                .isEqualTo("Para o curso Java é recomendado conhecimento básico de lógica.");
    }

    @Test
    void oFormatoPadraoSemOFormatadorTrariaOsMetadadosPoisEPorIssoQueOFormatadorExiste() {
        var documento = gravar("Texto do trecho.").get(0);

        // Comportamento da biblioteca que o formatador neutraliza: com o formatador padrão, o modo EMBED incluiria
        // os metadados (aqui, o título e a fonte) no texto enviado ao modelo.
        String comFormatadorPadrao = documento.getFormattedContent(
                org.springframework.ai.document.Document.DEFAULT_CONTENT_FORMATTER, MetadataMode.EMBED);
        assertThat(comFormatadorPadrao).contains("Título secreto do metadado", "Fonte secreta do metadado");
        assertThat(documento.getFormattedContent(MetadataMode.EMBED)).doesNotContain("secreto");
    }

    @Test
    void osMetadadosContinuamGravadosNoTrecho() {
        var documento = gravar("Texto do trecho.").get(0);

        assertThat(documento.getMetadata()).containsEntry("titulo", "Título secreto do metadado")
                .containsEntry("fonte", "Fonte secreta do metadado").containsEntry("status", "PROCESSANDO");
        assertThat(documento.getText()).isEqualTo("Texto do trecho.");
    }
}
