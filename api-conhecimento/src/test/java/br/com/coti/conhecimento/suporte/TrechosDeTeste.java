package br.com.coti.conhecimento.suporte;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Grava trechos direto na {@code vector_store} com um vetor escolhido para ter um cosseno exato em relação à
 * pergunta. Isso permite testar limiar, ordem e filtros sem depender de um modelo de embedding real.
 */
public final class TrechosDeTeste {

    private TrechosDeTeste() {
    }

    /** Vetor da pergunta: o mesmo que o {@link EmbeddingModelFalso} devolve para o texto. */
    public static float[] vetorDaPergunta(String pergunta) {
        return EmbeddingModelFalso.vetorDe(pergunta);
    }

    /**
     * Vetor de norma 1 cujo cosseno com {@code base} (também de norma 1) é exatamente {@code cosseno}:
     * {@code c * base + sqrt(1 - c²) * ortogonal}, com o ortogonal obtido por Gram-Schmidt sobre um vetor fixo
     * derivado de {@code semente}.
     */
    public static float[] comCosseno(float[] base, double cosseno, String semente) {
        float[] outro = EmbeddingModelFalso.vetorDe("ortogonal:" + semente);
        double produto = 0;
        for (int i = 0; i < base.length; i++) {
            produto += (double) outro[i] * base[i];
        }
        double[] ortogonal = new double[base.length];
        double norma = 0;
        for (int i = 0; i < base.length; i++) {
            ortogonal[i] = outro[i] - produto * base[i];
            norma += ortogonal[i] * ortogonal[i];
        }
        norma = Math.sqrt(norma);
        double seno = Math.sqrt(Math.max(0, 1 - cosseno * cosseno));
        float[] resultado = new float[base.length];
        for (int i = 0; i < base.length; i++) {
            resultado[i] = (float) (cosseno * base[i] + seno * ortogonal[i] / norma);
        }
        return resultado;
    }

    /**
     * Esvazia a {@code vector_store} e o índice HNSW com {@code TRUNCATE}, como dono do esquema (o papel da aplicação
     * não tem esse privilégio, de propósito). Um {@code DELETE} deixa entradas mortas no grafo do HNSW até o VACUUM,
     * o que degrada a busca aproximada e deixaria os testes de busca dependentes da ordem de execução.
     */
    public static void zerar() {
        try (Connection conexao = DriverManager.getConnection(PostgresContainerConfig.urlJdbc(),
                PostgresContainerConfig.USUARIO_DONO, PostgresContainerConfig.senhaDono());
             Statement comando = conexao.createStatement()) {
            comando.execute("TRUNCATE vector_store");
        } catch (SQLException falha) {
            throw new IllegalStateException("Não foi possível esvaziar a vector_store.", falha);
        }
    }

    public static String literalVetor(float[] vetor) {
        StringBuilder texto = new StringBuilder("[");
        for (int i = 0; i < vetor.length; i++) {
            if (i > 0) {
                texto.append(',');
            }
            texto.append(String.format(Locale.ROOT, "%.8f", vetor[i]));
        }
        return texto.append(']').toString();
    }

    /** Um trecho a gravar. {@code vigenteAte} no formato {@code AAAAMMDD} (99991231 = sem vigência). */
    public record Trecho(UUID id, UUID documentoId, String texto, String titulo, String fonte, String categoria,
                         String status, String modelo, int vigenteAte, int ordem, double cosseno) {

        public static Trecho publicado(String texto, double cosseno) {
            return new Trecho(UUID.randomUUID(), UUID.randomUUID(), texto, "Título", "Fonte", "CURSO", "PUBLICADO",
                    "text-embedding-3-small", 99_991_231, 0, cosseno);
        }

        public Trecho comStatus(String novo) {
            return new Trecho(id, documentoId, texto, titulo, fonte, categoria, novo, modelo, vigenteAte, ordem, cosseno);
        }

        public Trecho comModelo(String novo) {
            return new Trecho(id, documentoId, texto, titulo, fonte, categoria, status, novo, vigenteAte, ordem, cosseno);
        }

        public Trecho comVigencia(int novo) {
            return new Trecho(id, documentoId, texto, titulo, fonte, categoria, status, modelo, novo, ordem, cosseno);
        }

        public Trecho comCategoria(String nova) {
            return new Trecho(id, documentoId, texto, titulo, fonte, nova, status, modelo, vigenteAte, ordem, cosseno);
        }

        public Trecho comDocumento(UUID novo) {
            return new Trecho(id, novo, texto, titulo, fonte, categoria, status, modelo, vigenteAte, ordem, cosseno);
        }
    }

    public static void gravar(JdbcClient jdbc, String pergunta, Trecho trecho) {
        float[] vetor = comCosseno(vetorDaPergunta(pergunta), trecho.cosseno(), trecho.id().toString());
        Map<String, Object> parametros = Map.of(
                "id", trecho.id(),
                "texto", trecho.texto(),
                "metadados", metadados(trecho),
                "vetor", literalVetor(vetor));
        jdbc.sql("""
                INSERT INTO vector_store (id, content, metadata, embedding)
                VALUES (:id, :texto, CAST(:metadados AS json), CAST(:vetor AS vector))""")
                .params(parametros).update();
    }

    private static String metadados(Trecho t) {
        return """
                {"documentoId":"%s","titulo":"%s","fonte":"%s","categoria":"%s","ordem":%d,"status":"%s",\
                "modeloEmbedding":"%s","vigenteAte":%d,"cadastradoEm":"2026-10-23T10:00:00-03:00"}"""
                .formatted(t.documentoId(), t.titulo(), t.fonte(), t.categoria(), t.ordem(), t.status(),
                        t.modelo(), t.vigenteAte());
    }
}
