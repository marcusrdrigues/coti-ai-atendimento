package br.com.coti.conhecimento.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import br.com.coti.conhecimento.aplicacao.porta.RepositorioDocumentoPort;
import br.com.coti.conhecimento.aplicacao.porta.RepositorioVetorialPort;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.HashConteudo;
import br.com.coti.conhecimento.dominio.IdTrecho;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import br.com.coti.conhecimento.dominio.Trecho;
import br.com.coti.conhecimento.suporte.EmbeddingModelFalso;
import br.com.coti.conhecimento.suporte.TesteWeb;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** ADR-07: a publicação muda trechos e documento na mesma transação, ou não muda nada. */
@TesteWeb
class PublicacaoAtomicaIT {

    @Autowired
    private PublicarDocumentoService publicacao;

    @MockitoSpyBean
    private RepositorioDocumentoPort documentos;

    @Autowired
    private RepositorioVetorialPort vetorial;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EmbeddingModelFalso embeddings;

    @BeforeEach
    void limpar() {
        jdbc.sql("DELETE FROM documento").update();
        jdbc.sql("DELETE FROM vector_store").update();
        embeddings.reiniciar();
    }

    private Documento processandoComTrechos(int quantidade) {
        Documento doc = Documento.emProcessamento(UUID.randomUUID(), "Título", "Conteúdo", "Fonte",
                Categoria.CURSO, HashConteudo.sha256(UUID.randomUUID().toString()), "text-embedding-3-small", null,
                "gestor", OffsetDateTime.now());
        documentos.inserir(doc);
        List<Trecho> trechos = IntStream.range(0, quantidade)
                .mapToObj(i -> new Trecho(IdTrecho.de(doc.id(), i), i, "Trecho " + i)).toList();
        if (!trechos.isEmpty()) {
            vetorial.gravarLote(doc, trechos);
        }
        return doc;
    }

    private List<String> statusDosTrechos(UUID id) {
        return jdbc.sql("SELECT metadata ->> 'status' FROM vector_store WHERE metadata ->> 'documentoId' = :id")
                .param("id", id.toString()).query(String.class).list();
    }

    private String statusDoDocumento(UUID id) {
        return jdbc.sql("SELECT status FROM documento WHERE id = :id").param("id", id).query(String.class).single();
    }

    @Test
    void publicaTrechosEDocumentoJuntos() {
        Documento doc = processandoComTrechos(3);

        Documento publicado = publicacao.publicar(doc, 3);

        assertThat(publicado.status()).isEqualTo(StatusDocumento.PUBLICADO);
        assertThat(statusDosTrechos(doc.id())).hasSize(3).containsOnly("PUBLICADO");
        assertThat(statusDoDocumento(doc.id())).isEqualTo("PUBLICADO");
        assertThat(jdbc.sql("SELECT quantidade_chunks FROM documento WHERE id = :id").param("id", doc.id())
                .query(Integer.class).single()).isEqualTo(3);
    }

    @Test
    void falhaAoAtualizarODocumentoDesfazAPromocaoDosTrechos() {
        Documento doc = processandoComTrechos(3);
        doThrow(new DataAccessResourceFailureException("conexão perdida")).when(documentos).atualizarEstado(any());

        assertThatThrownBy(() -> publicacao.publicar(doc, 3)).isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(statusDosTrechos(doc.id())).as("trechos continuam invisíveis à busca").hasSize(3)
                .containsOnly("PROCESSANDO");
        assertThat(statusDoDocumento(doc.id())).isEqualTo("PROCESSANDO");
    }

    @Test
    void quantidadeDivergenteDesfazTudo() {
        Documento doc = processandoComTrechos(3);

        assertThatThrownBy(() -> publicacao.publicar(doc, 5))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("diferem");

        assertThat(statusDosTrechos(doc.id())).containsOnly("PROCESSANDO");
        assertThat(statusDoDocumento(doc.id())).isEqualTo("PROCESSANDO");
    }

    @Test
    void semTrechosGravadosNaoPublica() {
        Documento doc = processandoComTrechos(0);

        assertThatThrownBy(() -> publicacao.publicar(doc, 0)).isInstanceOf(IllegalArgumentException.class);

        assertThat(statusDoDocumento(doc.id())).isEqualTo("PROCESSANDO");
    }

    @Test
    void naoPublicaDocumentoQueJaNaoEstaProcessando() {
        Documento doc = processandoComTrechos(2);
        Documento publicado = publicacao.publicar(doc, 2);

        assertThatThrownBy(() -> publicacao.publicar(publicado, 2)).isInstanceOf(IllegalStateException.class);
    }
}
