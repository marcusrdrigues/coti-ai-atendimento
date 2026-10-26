package br.com.coti.conhecimento.infraestrutura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.coti.conhecimento.aplicacao.porta.RepositorioDocumentoPort;
import br.com.coti.conhecimento.aplicacao.porta.RepositorioVetorialPort;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.HashConteudo;
import br.com.coti.conhecimento.dominio.IdTrecho;
import br.com.coti.conhecimento.dominio.Trecho;
import br.com.coti.conhecimento.suporte.EmbeddingModelFalso;
import br.com.coti.conhecimento.suporte.TesteWeb;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Limpeza na subida: documento PROCESSANDO há mais de 10 minutos, com ou sem trechos, vira ERRO_PROCESSAMENTO. */
@TesteWeb
class LimpezaDocumentosInterrompidosIT {

    @Autowired
    private LimpezaDocumentosInterrompidos limpeza;

    @Autowired
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

    private Documento processando(String chave, long minutosAtras, int trechos) {
        Documento doc = Documento.emProcessamento(UUID.randomUUID(), "Título " + chave, "Conteúdo " + chave, "Fonte",
                Categoria.CURSO, HashConteudo.sha256(chave + UUID.randomUUID()), "text-embedding-3-small", null,
                "gestor", OffsetDateTime.now().minusMinutes(minutosAtras));
        documentos.inserir(doc);
        if (trechos > 0) {
            vetorial.gravarLote(doc, java.util.stream.IntStream.range(0, trechos)
                    .mapToObj(i -> new Trecho(IdTrecho.de(doc.id(), i), i, "Trecho " + i + " de " + chave)).toList());
        }
        return doc;
    }

    private String status(UUID id) {
        return jdbc.sql("SELECT status FROM documento WHERE id = :id").param("id", id).query(String.class).single();
    }

    private long trechos(UUID id) {
        return jdbc.sql("SELECT count(*) FROM vector_store WHERE metadata ->> 'documentoId' = :id")
                .param("id", id.toString()).query(Long.class).single();
    }

    @Test
    void naSubidaRecolheOInterrompidoApagandoSeusTrechos() {
        Documento velho = processando("velho", 11, 3);

        limpeza.run(new DefaultApplicationArguments());

        assertThat(status(velho.id())).isEqualTo("ERRO_PROCESSAMENTO");
        assertThat(trechos(velho.id())).isZero();
    }

    @Test
    void naoMexeEmCadastroEmAndamentoNemEmDocumentoPublicado() {
        Documento recente = processando("recente", 2, 2);
        Documento outro = processando("outro", 30, 1);
        vetorial.promoverTrechos(outro.id());
        jdbc.sql("UPDATE documento SET status = 'PUBLICADO', quantidade_chunks = 1 WHERE id = :id")
                .param("id", outro.id()).update();

        limpeza.run(new DefaultApplicationArguments());

        assertThat(status(recente.id())).isEqualTo("PROCESSANDO");
        assertThat(trechos(recente.id())).isEqualTo(2);
        assertThat(status(outro.id())).isEqualTo("PUBLICADO");
        assertThat(trechos(outro.id())).isEqualTo(1);
    }

    @Test
    void recolheVariosEOInterrompidoSemTrechos() {
        List<Documento> velhos = List.of(processando("a", 15, 2), processando("b", 40, 0), processando("c", 600, 1));

        limpeza.run(new DefaultApplicationArguments());

        assertThat(velhos).allSatisfy(d -> {
            assertThat(status(d.id())).isEqualTo("ERRO_PROCESSAMENTO");
            assertThat(trechos(d.id())).isZero();
        });
    }

    @Test
    void odocumentoRecolhidoApareceNaListagemComoErro() {
        Documento velho = processando("velho", 20, 1);

        limpeza.run(new DefaultApplicationArguments());

        assertThat(documentos.buscarPorId(velho.id())).get()
                .satisfies(d -> assertThat(d.status().name()).isEqualTo("ERRO_PROCESSAMENTO"));
        assertThat(documentos.listar(br.com.coti.conhecimento.dominio.FiltroDocumentos.todos(), 0, 20).itens())
                .extracting(r -> r.id()).contains(velho.id());
    }

    @Test
    void rodarDuasVezesNaoFazMalNenhum() {
        Documento velho = processando("velho", 20, 2);

        limpeza.run(new DefaultApplicationArguments());
        limpeza.run(new DefaultApplicationArguments());

        assertThat(status(velho.id())).isEqualTo("ERRO_PROCESSAMENTO");
    }
}
