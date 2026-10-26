package br.com.coti.conhecimento.infraestrutura.vetorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.conhecimento.aplicacao.porta.RepositorioVetorialPort;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.HashConteudo;
import br.com.coti.conhecimento.dominio.IdTrecho;
import br.com.coti.conhecimento.dominio.Trecho;
import br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingException;
import br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingTransitoriaException;
import br.com.coti.conhecimento.suporte.EmbeddingModelFalso;
import br.com.coti.conhecimento.suporte.TesteWeb;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@TesteWeb
class PgVectorRepositorioAdapterIT {

    @Autowired
    private RepositorioVetorialPort vetorial;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EmbeddingModelFalso embeddings;

    @BeforeEach
    void limpar() {
        jdbc.sql("DELETE FROM vector_store").update();
        embeddings.reiniciar();
    }

    private static Documento documento(LocalDate vigencia) {
        return Documento.emProcessamento(UUID.randomUUID(), "Curso Java — pré-requisitos", "Conteúdo completo",
                "Site COTI, out/2026", Categoria.PRE_REQUISITO, HashConteudo.sha256(UUID.randomUUID().toString()),
                "text-embedding-3-small", vigencia, "gestor-conteudo", OffsetDateTime.parse("2026-10-23T10:00:00-03:00"));
    }

    private static List<Trecho> trechos(Documento documento, int quantidade) {
        return IntStream.range(0, quantidade)
                .mapToObj(i -> new Trecho(IdTrecho.de(documento.id(), i), i, "Trecho " + i + " do documento")).toList();
    }

    private long contar(UUID documentoId) {
        return jdbc.sql("SELECT count(*) FROM vector_store WHERE metadata ->> 'documentoId' = :id")
                .param("id", documentoId.toString()).query(Long.class).single();
    }

    @Test
    void deveGravarOsTrechosComIdsDeterministicosEOTextoOriginal() {
        Documento doc = documento(null);

        vetorial.gravarLote(doc, trechos(doc, 3));

        assertThat(jdbc.sql("SELECT id::text FROM vector_store ORDER BY (metadata ->> 'ordem')::int")
                .query(String.class).list())
                .containsExactly(IdTrecho.de(doc.id(), 0).toString(), IdTrecho.de(doc.id(), 1).toString(),
                        IdTrecho.de(doc.id(), 2).toString());
        assertThat(jdbc.sql("SELECT content FROM vector_store ORDER BY (metadata ->> 'ordem')::int")
                .query(String.class).list())
                .containsExactly("Trecho 0 do documento", "Trecho 1 do documento", "Trecho 2 do documento");
    }

    @Test
    void deveGravarTodosOsMetadadosObrigatoriosComStatusProcessando() {
        Documento doc = documento(LocalDate.of(2027, 6, 30));

        vetorial.gravarLote(doc, trechos(doc, 1));

        Map<String, Object> linha = jdbc.sql("""
                SELECT metadata ->> 'documentoId' AS documento_id, metadata ->> 'titulo' AS titulo,
                       metadata ->> 'fonte' AS fonte, metadata ->> 'categoria' AS categoria,
                       (metadata ->> 'ordem')::int AS ordem, metadata ->> 'status' AS status,
                       metadata ->> 'modeloEmbedding' AS modelo, (metadata ->> 'vigenteAte')::int AS vigencia,
                       metadata ->> 'cadastradoEm' AS cadastrado_em,
                       jsonb_typeof((metadata::jsonb) -> 'vigenteAte') AS tipo_vigencia
                  FROM vector_store""").query().singleRow();

        assertThat(linha).containsEntry("documento_id", doc.id().toString())
                .containsEntry("titulo", "Curso Java — pré-requisitos")
                .containsEntry("fonte", "Site COTI, out/2026")
                .containsEntry("categoria", "PRE_REQUISITO")
                .containsEntry("ordem", 0)
                .containsEntry("status", "PROCESSANDO")
                .containsEntry("modelo", "text-embedding-3-small")
                .containsEntry("vigencia", 20270630)
                .containsEntry("tipo_vigencia", "number");
        assertThat((String) linha.get("cadastrado_em")).startsWith("2026-10-23T10:00:00");
    }

    @Test
    void semVigenciaOMetadadoVale99991231() {
        Documento doc = documento(null);

        vetorial.gravarLote(doc, trechos(doc, 1));

        assertThat(jdbc.sql("SELECT (metadata ->> 'vigenteAte')::int FROM vector_store").query(Integer.class).single())
                .isEqualTo(99_991_231);
    }

    @Test
    void oEmbeddingTem1536DimensoesEOEnviadoAoProvedorEhSoOTexto() {
        Documento doc = documento(null);

        vetorial.gravarLote(doc, trechos(doc, 2));

        assertThat(jdbc.sql("SELECT DISTINCT vector_dims(embedding) FROM vector_store").query(Integer.class).single())
                .isEqualTo(1536);
        assertThat(embeddings.todosOsTextos()).containsExactlyInAnyOrder("Trecho 0 do documento", "Trecho 1 do documento");
    }

    @Test
    void regravarOMesmoLoteNaoDuplicaTrechos() {
        Documento doc = documento(null);
        List<Trecho> lote = trechos(doc, 3);

        vetorial.gravarLote(doc, lote);
        vetorial.gravarLote(doc, lote);

        assertThat(contar(doc.id())).isEqualTo(3);
    }

    @Test
    void promoverMudaSoOsTrechosDoDocumentoQueEstaoProcessando() {
        Documento a = documento(null);
        Documento b = documento(null);
        vetorial.gravarLote(a, trechos(a, 3));
        vetorial.gravarLote(b, trechos(b, 2));

        int promovidos = vetorial.promoverTrechos(a.id());

        assertThat(promovidos).isEqualTo(3);
        assertThat(jdbc.sql("SELECT metadata ->> 'status' FROM vector_store WHERE metadata ->> 'documentoId' = :id")
                .param("id", a.id().toString()).query(String.class).list()).containsOnly("PUBLICADO");
        assertThat(jdbc.sql("SELECT metadata ->> 'status' FROM vector_store WHERE metadata ->> 'documentoId' = :id")
                .param("id", b.id().toString()).query(String.class).list()).containsOnly("PROCESSANDO");
        assertThat(vetorial.promoverTrechos(a.id())).as("já publicados: nada a promover").isZero();
    }

    @Test
    void promoverPreservaOsDemaisMetadados() {
        Documento doc = documento(LocalDate.of(2027, 6, 30));
        vetorial.gravarLote(doc, trechos(doc, 1));

        vetorial.promoverTrechos(doc.id());

        Map<String, Object> linha = jdbc.sql("""
                SELECT (metadata ->> 'vigenteAte')::int AS vigencia, (metadata ->> 'ordem')::int AS ordem,
                       metadata ->> 'titulo' AS titulo, metadata ->> 'modeloEmbedding' AS modelo,
                       jsonb_typeof((metadata::jsonb) -> 'ordem') AS tipo_ordem
                  FROM vector_store""").query().singleRow();
        assertThat(linha).containsEntry("vigencia", 20270630).containsEntry("ordem", 0)
                .containsEntry("titulo", "Curso Java — pré-requisitos")
                .containsEntry("modelo", "text-embedding-3-small").containsEntry("tipo_ordem", "number");
    }

    @Test
    void removerApagaTodosOsTrechosDoDocumentoEMaisNada() {
        Documento a = documento(null);
        Documento b = documento(null);
        vetorial.gravarLote(a, trechos(a, 4));
        vetorial.gravarLote(b, trechos(b, 2));
        vetorial.promoverTrechos(a.id());

        vetorial.removerPorDocumento(a.id());

        assertThat(contar(a.id())).isZero();
        assertThat(contar(b.id())).isEqualTo(2);
    }

    @Test
    void removerDocumentoSemTrechosNaoFalha() {
        vetorial.removerPorDocumento(UUID.randomUUID());
    }

    @Test
    void falhaTransitoriaDoProvedorViraExcecaoDeDominioTransitoriaESemTrechosGravados() {
        Documento doc = documento(null);
        embeddings.falharSempre(EmbeddingModelFalso::limiteDeRequisicoes);

        assertThatThrownBy(() -> vetorial.gravarLote(doc, trechos(doc, 2)))
                .isInstanceOf(FalhaEmbeddingTransitoriaException.class);

        assertThat(contar(doc.id())).isZero();
    }

    @Test
    void falhaDefinitivaDoProvedorViraExcecaoDeDominioDefinitiva() {
        Documento doc = documento(null);
        embeddings.falharSempre(EmbeddingModelFalso::credencialRecusada);

        assertThatThrownBy(() -> vetorial.gravarLote(doc, trechos(doc, 2)))
                .isInstanceOf(FalhaEmbeddingException.class);

        assertThat(contar(doc.id())).isZero();
        assertThat(embeddings.chamadas()).isEqualTo(1);
    }
}
