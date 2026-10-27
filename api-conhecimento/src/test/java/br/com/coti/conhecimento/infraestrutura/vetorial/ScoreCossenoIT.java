package br.com.coti.conhecimento.infraestrutura.vetorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import br.com.coti.conhecimento.suporte.EmbeddingModelFalso;
import br.com.coti.conhecimento.suporte.TesteWeb;
import br.com.coti.conhecimento.suporte.TrechosDeTeste;
import br.com.coti.conhecimento.suporte.TrechosDeTeste.Trecho;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Spike T037: como o PgVectorStore do Spring AI 2.0.1 devolve a similaridade com {@code COSINE_DISTANCE} e se o
 * filtro de vigência por inteiro funciona dentro da busca. Os trechos têm cosseno exato com a pergunta (vetores
 * construídos), então o score devolvido pode ser comparado com o valor esperado. Versionado para detectar mudança
 * de comportamento numa atualização da biblioteca.
 */
@TesteWeb
class ScoreCossenoIT {

    private static final String PERGUNTA = "o que preciso saber antes de fazer o curso de Java?";

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EmbeddingModelFalso embeddings;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transacao;

    @BeforeEach
    void limpar() {
        TrechosDeTeste.zerar();
        embeddings.reiniciar();
    }

    private void gravar(Trecho... trechos) {
        for (Trecho trecho : trechos) {
            TrechosDeTeste.gravar(jdbc, PERGUNTA, trecho);
        }
    }

    private static SearchRequest.Builder busca(int topK) {
        return SearchRequest.builder().query(PERGUNTA).topK(topK);
    }

    @Test
    void oScoreEASimilaridadeDeCossenoENaoADistancia() {
        gravar(Trecho.publicado("c95", 0.95), Trecho.publicado("c80", 0.80), Trecho.publicado("c55", 0.55));

        List<Document> resultado = vectorStore.similaritySearch(busca(10).build());

        assertThat(resultado).extracting(Document::getText).containsExactly("c95", "c80", "c55");
        assertThat(resultado).extracting(Document::getScore)
                .satisfiesExactly(
                        s -> assertThat(s).isCloseTo(0.95, within(1e-3)),
                        s -> assertThat(s).isCloseTo(0.80, within(1e-3)),
                        s -> assertThat(s).isCloseTo(0.55, within(1e-3)));
    }

    @Test
    void aPerguntaIdenticaTemScoreUm() {
        gravar(Trecho.publicado("igual", 1.0));

        List<Document> resultado = vectorStore.similaritySearch(busca(1).build());

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getScore()).isCloseTo(1.0, within(1e-3));
    }

    @Test
    void cossenoNegativoNuncaVoltaPoisOLimiarPadraoDoSpringAIEZero() {
        gravar(Trecho.publicado("oposto", -0.5), Trecho.publicado("ortogonal", 0.0), Trecho.publicado("fraco", 0.10));

        List<Document> resultado = vectorStore.similaritySearch(busca(10).build());

        // Sem limiar explícito vale o padrão 0 (SIMILARITY_THRESHOLD_ACCEPT_ALL): o score devolvido fica sempre
        // em [0, 1], como o contrato exige. O limiar de 0,70 do projeto é explícito e mais estrito.
        assertThat(resultado).extracting(Document::getText).doesNotContain("oposto");
        assertThat(resultado).allSatisfy(d -> assertThat(d.getScore()).isBetween(0.0, 1.0));
        assertThat(resultado).extracting(Document::getText).contains("fraco");
    }

    @Test
    void oLimiarCorta_oQueFicaAbaixoEMantemOQueFicaAcima() {
        gravar(Trecho.publicado("c90", 0.90), Trecho.publicado("c75", 0.75), Trecho.publicado("c65", 0.65),
                Trecho.publicado("c30", 0.30));

        List<Document> resultado = vectorStore.similaritySearch(busca(10).similarityThreshold(0.70).build());

        assertThat(resultado).extracting(Document::getText).containsExactly("c90", "c75");
        assertThat(resultado).allSatisfy(d -> assertThat(d.getScore()).isGreaterThanOrEqualTo(0.70));
    }

    @Test
    void oLimiarEInclusivo() {
        gravar(Trecho.publicado("c70", 0.70));

        List<Document> exato = vectorStore.similaritySearch(busca(10).similarityThreshold(0.699).build());
        List<Document> acima = vectorStore.similaritySearch(busca(10).similarityThreshold(0.701).build());

        assertThat(exato).hasSize(1);
        assertThat(acima).isEmpty();
    }

    @Test
    void topKLimitaAQuantidadeDevolvidaEMantemOsMaisSimilares() {
        for (int i = 0; i < 8; i++) {
            gravar(Trecho.publicado("c" + i, 0.99 - i * 0.02));
        }

        List<Document> resultado = vectorStore.similaritySearch(busca(3).build());

        assertThat(resultado).extracting(Document::getText).containsExactly("c0", "c1", "c2");
    }

    @Test
    void filtroDeVigenciaPorInteiroComGteFuncionaDentroDaBusca() {
        gravar(Trecho.publicado("sem-vigencia", 0.90),
                Trecho.publicado("vence-hoje", 0.89).comVigencia(20_261_027),
                Trecho.publicado("venceu-ontem", 0.88).comVigencia(20_261_026),
                Trecho.publicado("vence-em-2027", 0.87).comVigencia(20_270_630));
        var b = new FilterExpressionBuilder();

        List<Document> resultado = vectorStore.similaritySearch(
                busca(10).filterExpression(b.gte("vigenteAte", 20_261_027).build()).build());

        assertThat(resultado).extracting(Document::getText)
                .containsExactly("sem-vigencia", "vence-hoje", "vence-em-2027");
    }

    @Test
    void filtroCompostoDePublicacaoModeloEVigenciaComLimiar() {
        gravar(Trecho.publicado("ok", 0.90),
                Trecho.publicado("em-revisao", 0.95).comStatus("EM_REVISAO"),
                Trecho.publicado("processando", 0.94).comStatus("PROCESSANDO"),
                Trecho.publicado("outro-modelo", 0.93).comModelo("text-embedding-3-large"),
                Trecho.publicado("vencido", 0.92).comVigencia(20_260_101),
                Trecho.publicado("fraco", 0.40));
        var b = new FilterExpressionBuilder();
        var filtro = b.and(b.and(b.eq("status", "PUBLICADO"), b.eq("modeloEmbedding", "text-embedding-3-small")),
                b.gte("vigenteAte", 20_261_027)).build();

        List<Document> resultado = vectorStore.similaritySearch(
                busca(10).similarityThreshold(0.70).filterExpression(filtro).build());

        assertThat(resultado).extracting(Document::getText).containsExactly("ok");
    }

    @Test
    void valorComAspasNoFiltroEUmaConstanteELimitaNaoViraConsulta() {
        gravar(Trecho.publicado("ok", 0.90));
        var b = new FilterExpressionBuilder();

        List<Document> resultado = vectorStore.similaritySearch(busca(10)
                .filterExpression(b.eq("status", "PUBLICADO' || '1").build()).build());

        assertThat(resultado).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM vector_store").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void comOIndiceForcadoEMuitasLinhasApagadasOScanIterativoEvitaPerderResultados() {
        // Achado da Fase 5. O HNSW mantém entradas de linhas apagadas até o VACUUM e o filtro e o limiar da busca
        // são aplicados depois do scan do índice (ef_search = 40). Sem o scan iterativo do pgvector 0.8, o índice
        // devolvia só candidatos mortos e a busca voltava vazia, mesmo havendo trechos válidos.
        // enable_seqscan = off força o uso do índice (com tabela pequena o planejador faria um scan exato).
        for (int i = 0; i < 400; i++) {
            gravar(Trecho.publicado("apagado-" + i, 0.99));
        }
        jdbc.sql("DELETE FROM vector_store").update();
        gravar(Trecho.publicado("a", 0.90), Trecho.publicado("b", 0.85), Trecho.publicado("c", 0.80));
        SearchRequest requisicao = busca(3).similarityThreshold(0.70).build();

        List<Document> comScanIterativo = emTransacao(() -> vectorStore.similaritySearch(requisicao),
                "SET LOCAL enable_seqscan = off");
        List<Document> semScanIterativo = emTransacao(() -> vectorStore.similaritySearch(requisicao),
                "SET LOCAL enable_seqscan = off", "SET LOCAL hnsw.iterative_scan = off");

        assertThat(comScanIterativo).extracting(Document::getText).containsExactly("a", "b", "c");
        assertThat(semScanIterativo).as("o defeito que a configuração evita").isEmpty();
    }

    private <T> T emTransacao(java.util.function.Supplier<T> consulta, String... ajustes) {
        return transacao.execute(status -> {
            for (String ajuste : ajustes) {
                jdbc.sql(ajuste).update();
            }
            return consulta.get();
        });
    }

    @Test
    void aConexaoDaAplicacaoUsaOScanIterativoDoHnsw() {
        String configuracao = jdbc.sql("SELECT current_setting('hnsw.iterative_scan')").query(String.class).single();

        assertThat(configuracao).isEqualTo("strict_order");
    }

    @Test
    void comMuitosTrechosNaoPublicadosMaisProximosABuscaAindaDevolveOsPublicados() {
        // Risco do plano: HNSW com filtro pode devolver menos de k. 300 trechos não publicados mais próximos da
        // pergunta do que os 5 publicados disputam o espaço de candidatos do índice.
        for (int i = 0; i < 300; i++) {
            gravar(Trecho.publicado("rascunho-" + i, 0.97 - (i % 20) * 0.001).comStatus("EM_REVISAO"));
        }
        for (int i = 0; i < 5; i++) {
            gravar(Trecho.publicado("publicado-" + i, 0.85 - i * 0.01));
        }
        jdbc.sql("ANALYZE vector_store").update();
        var b = new FilterExpressionBuilder();

        List<Document> resultado = vectorStore.similaritySearch(busca(5).similarityThreshold(0.70)
                .filterExpression(b.eq("status", "PUBLICADO").build()).build());

        assertThat(resultado).extracting(Document::getText)
                .containsExactly("publicado-0", "publicado-1", "publicado-2", "publicado-3", "publicado-4");
    }
}
