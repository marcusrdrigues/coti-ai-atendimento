package br.com.coti.conhecimento.infraestrutura.vetorial;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.coti.conhecimento.suporte.EmbeddingModelFalso;
import br.com.coti.conhecimento.suporte.TesteWeb;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Spike T031: comportamentos do PgVectorStore do Spring AI 2.0.1 dos quais a ingestão depende. Fica versionado
 * como teste para detectar mudança de comportamento numa atualização da biblioteca.
 */
@TesteWeb
class PgVectorStoreComportamentoIT {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EmbeddingModelFalso embeddings;

    @BeforeEach
    void limpar() {
        jdbc.sql("DELETE FROM vector_store").update();
        embeddings.reiniciar();
    }

    private static Document documento(UUID id, String texto, UUID documentoId) {
        return Document.builder().id(id.toString()).text(texto)
                .metadata(Map.of("documentoId", documentoId.toString(), "ordem", 3, "vigenteAte", 20270630,
                        "status", "PROCESSANDO", "titulo", "Título secreto do metadado"))
                .build();
    }

    private long contar() {
        return jdbc.sql("SELECT count(*) FROM vector_store").query(Long.class).single();
    }

    @Test
    void aColunaMetadataEJsonENumerosContinuamNumeros() {
        UUID doc = UUID.randomUUID();
        vectorStore.add(List.of(documento(UUID.randomUUID(), "texto", doc)));

        Map<String, Object> linha = jdbc.sql("""
                SELECT pg_typeof(metadata)::text AS tipo,
                       jsonb_typeof((metadata::jsonb) -> 'vigenteAte') AS tipo_vigencia,
                       (metadata ->> 'vigenteAte')::int AS vigencia,
                       (metadata ->> 'ordem')::int AS ordem
                  FROM vector_store""").query().singleRow();

        assertThat(linha.get("tipo")).isEqualTo("json");
        assertThat(linha.get("tipo_vigencia")).isEqualTo("number");
        assertThat(linha.get("vigencia")).isEqualTo(20270630);
        assertThat(linha.get("ordem")).isEqualTo(3);
    }

    @Test
    void addComIdJaExistenteAtualizaALinhaEmVezDeDuplicar() {
        UUID id = UUID.randomUUID();
        UUID doc = UUID.randomUUID();

        vectorStore.add(List.of(documento(id, "primeira versão", doc)));
        vectorStore.add(List.of(documento(id, "segunda versão", doc)));

        assertThat(contar()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT content FROM vector_store").query(String.class).single()).isEqualTo("segunda versão");
    }

    @Test
    void deleteComExpressaoDeFiltroApagaSoOsTrechosDoDocumento() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        vectorStore.add(List.of(documento(UUID.randomUUID(), "a1", a), documento(UUID.randomUUID(), "a2", a),
                documento(UUID.randomUUID(), "b1", b)));

        vectorStore.delete(new FilterExpressionBuilder().eq("documentoId", a.toString()).build());

        assertThat(jdbc.sql("SELECT metadata ->> 'documentoId' FROM vector_store").query(String.class).list())
                .containsExactly(b.toString());
    }

    @Test
    void deleteComExpressaoEmTextoTambemFunciona() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        vectorStore.add(List.of(documento(UUID.randomUUID(), "a1", a), documento(UUID.randomUUID(), "b1", b)));

        vectorStore.delete("documentoId == '" + b + "'");

        assertThat(jdbc.sql("SELECT metadata ->> 'documentoId' FROM vector_store").query(String.class).list())
                .containsExactly(a.toString());
    }

    @Test
    void oEmbeddingUsaSoOTextoDoTrechoENaoOsMetadados() {
        vectorStore.add(List.of(documento(UUID.randomUUID(), "texto do trecho", UUID.randomUUID())));

        assertThat(embeddings.todosOsTextos()).containsExactly("texto do trecho");
        assertThat(embeddings.todosOsTextos().get(0)).doesNotContain("secreto").doesNotContain("PROCESSANDO");
    }

    @Test
    void oVetorGravadoTemAsDimensoesDaMigracao() {
        vectorStore.add(List.of(documento(UUID.randomUUID(), "texto", UUID.randomUUID())));

        assertThat(jdbc.sql("SELECT vector_dims(embedding) FROM vector_store").query(Integer.class).single())
                .isEqualTo(1536);
    }

    @Test
    void lotesGrandesSaoQuebradosEmVariasChamadasAoProvedor() {
        UUID doc = UUID.randomUUID();
        String texto = "palavra de teste ".repeat(90); // ~1.500 caracteres
        List<Document> lote = IntStream.range(0, 60)
                .mapToObj(i -> documento(UUID.randomUUID(), texto + i, doc)).toList();

        vectorStore.add(lote);

        assertThat(contar()).isEqualTo(60);
        assertThat(embeddings.chamadas()).as("o BatchingStrategy respeita o limite de tokens por chamada")
                .isGreaterThan(1);
        assertThat(embeddings.todosOsTextos()).hasSize(60);
    }

    @Test
    void promoverTrechosMudaOStatusSemAlterarOsDemaisMetadados() {
        UUID doc = UUID.randomUUID();
        vectorStore.add(List.of(documento(UUID.randomUUID(), "t1", doc), documento(UUID.randomUUID(), "t2", doc)));

        int promovidos = jdbc.sql("""
                UPDATE vector_store
                   SET metadata = jsonb_set(metadata::jsonb, '{status}', to_jsonb(CAST(:novo AS text)))::json
                 WHERE metadata ->> 'documentoId' = :doc AND metadata ->> 'status' = 'PROCESSANDO'""")
                .param("novo", "PUBLICADO").param("doc", doc.toString()).update();

        assertThat(promovidos).isEqualTo(2);
        Map<String, Object> linha = jdbc.sql("""
                SELECT metadata ->> 'status' AS status, (metadata ->> 'vigenteAte')::int AS vigencia,
                       jsonb_typeof((metadata::jsonb) -> 'ordem') AS tipo_ordem
                  FROM vector_store LIMIT 1""").query().singleRow();
        assertThat(linha).containsEntry("status", "PUBLICADO").containsEntry("vigencia", 20270630)
                .containsEntry("tipo_ordem", "number");
    }
}
