package br.com.coti.conhecimento.infraestrutura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.conhecimento.suporte.PostgresContainerConfig;
import br.com.coti.conhecimento.suporte.TesteWeb;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * As migrações Flyway foram aplicadas, como o papel dono, ao subir o contexto (PostgreSQL 18 + pgvector em
 * Testcontainers). Aqui se confere o resultado e a separação de privilégios entre dono e aplicação.
 */
@TesteWeb
class MigracoesIT {

    private static final String HASH_A = "a".repeat(64);

    private Connection comoDono() throws SQLException {
        return DriverManager.getConnection(PostgresContainerConfig.urlJdbc(),
                PostgresContainerConfig.USUARIO_DONO, PostgresContainerConfig.senhaDono());
    }

    private Connection comoApp() throws SQLException {
        return DriverManager.getConnection(PostgresContainerConfig.urlJdbc(),
                PostgresContainerConfig.USUARIO_APP, PostgresContainerConfig.senhaApp());
    }

    @AfterEach
    void limpar() throws SQLException {
        try (Connection conexao = comoApp(); Statement s = conexao.createStatement()) {
            s.executeUpdate("DELETE FROM documento");
            s.executeUpdate("DELETE FROM vector_store");
        }
    }

    private List<String> consultar(Connection conexao, String sql) throws SQLException {
        List<String> linhas = new ArrayList<>();
        try (Statement s = conexao.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                linhas.add(rs.getString(1));
            }
        }
        return linhas;
    }

    @Test
    void deveTerAplicadoAsTresMigracoesComSucesso() throws SQLException {
        try (Connection c = comoApp()) {
            assertThat(consultar(c, "SELECT version || ':' || success FROM flyway_schema_history "
                    + "WHERE version IS NOT NULL ORDER BY installed_rank"))
                    .containsExactly("1:true", "2:true", "3:true");
        }
    }

    @Test
    void deveTerAsExtensoesExigidasPeloPgVectorStore() throws SQLException {
        try (Connection c = comoApp()) {
            assertThat(consultar(c, "SELECT extname FROM pg_extension")).contains("vector", "hstore", "uuid-ossp");
            assertThat(consultar(c, "SELECT extversion FROM pg_extension WHERE extname = 'vector'"))
                    .singleElement().asString().startsWith("0.8");
        }
    }

    @Test
    void vectorStoreTemEmbeddingDe1536DimensoesEIndiceHnswDeCosseno() throws SQLException {
        try (Connection c = comoApp()) {
            assertThat(consultar(c, "SELECT format_type(atttypid, atttypmod) FROM pg_attribute "
                    + "WHERE attrelid = 'vector_store'::regclass AND attname = 'embedding'"))
                    .containsExactly("vector(1536)");
            assertThat(consultar(c, "SELECT indexdef FROM pg_indexes WHERE tablename = 'vector_store' "
                    + "AND indexname = 'vector_store_embedding_hnsw_idx'"))
                    .singleElement().asString().contains("USING hnsw").contains("vector_cosine_ops");
            assertThat(consultar(c, "SELECT column_name FROM information_schema.columns "
                    + "WHERE table_name = 'vector_store' ORDER BY ordinal_position"))
                    .containsExactly("id", "content", "metadata", "embedding");
        }
    }

    @Test
    void documentoTemAsColunasEOsIndicesDaArquitetura() throws SQLException {
        try (Connection c = comoApp()) {
            assertThat(consultar(c, "SELECT column_name FROM information_schema.columns "
                    + "WHERE table_name = 'documento' ORDER BY ordinal_position"))
                    .containsExactly("id", "titulo", "conteudo", "fonte", "categoria", "status", "pendencias",
                            "hash_conteudo", "quantidade_chunks", "modelo_embedding", "vigente_ate",
                            "cadastrado_por", "cadastrado_em");
            assertThat(consultar(c, "SELECT indexname FROM pg_indexes WHERE tablename = 'documento'"))
                    .contains("documento_cadastrado_em_idx", "documento_status_idx", "documento_categoria_idx");
        }
    }

    @Test
    void aAplicacaoNaoExecutaDdl() throws SQLException {
        try (Connection c = comoApp(); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.execute("CREATE TABLE intrusa (i int)"))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
            assertThatThrownBy(() -> s.execute("ALTER TABLE documento ADD COLUMN extra int"))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
            assertThatThrownBy(() -> s.execute("DROP TABLE documento"))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
            assertThatThrownBy(() -> s.execute("DROP TABLE vector_store"))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
        }
    }

    @Test
    void aAplicacaoFazDmlNasTabelasCriadasPeloDono() throws SQLException {
        try (Connection c = comoApp(); Statement s = c.createStatement()) {
            assertThat(s.executeUpdate("INSERT INTO documento (id, titulo, conteudo, fonte, categoria, status, "
                    + "hash_conteudo, quantidade_chunks, modelo_embedding, cadastrado_por, cadastrado_em) VALUES ('"
                    + UUID.randomUUID() + "', 't', 'c', 'f', 'CURSO', 'EM_REVISAO', '" + HASH_A
                    + "', 0, 'm', 'g', now())")).isEqualTo(1);
            assertThat(s.executeUpdate("UPDATE documento SET titulo = 'novo'")).isEqualTo(1);
            assertThat(s.executeUpdate("DELETE FROM documento")).isEqualTo(1);
            assertThat(s.executeUpdate("INSERT INTO vector_store (content, metadata) VALUES ('c', '{}')")).isEqualTo(1);
            assertThat(s.executeUpdate("DELETE FROM vector_store")).isEqualTo(1);
        }
    }

    @Test
    void oDonoNaoEhSuperusuarioMasCriaObjetos() throws SQLException {
        try (Connection c = comoDono(); Statement s = c.createStatement()) {
            assertThat(consultar(c, "SELECT rolsuper::text FROM pg_roles WHERE rolname = current_user"))
                    .containsExactly("false");
            s.execute("CREATE TABLE tabela_do_dono (i int)");
            s.execute("DROP TABLE tabela_do_dono");
        }
    }

    @Test
    void oBancoPadraoNaoAceitaConexaoDaAplicacao() {
        String url = PostgresContainerConfig.urlJdbc().replace("/" + PostgresContainerConfig.BANCO, "/postgres");

        assertThatThrownBy(() -> DriverManager.getConnection(url, PostgresContainerConfig.USUARIO_APP,
                PostgresContainerConfig.senhaApp()))
                .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
    }

    @Test
    void checkDeStatusECategoriaRecusaValoresInvalidos() throws SQLException {
        try (Connection c = comoApp()) {
            assertThat(tentarInserir(c, "CURSO", "INVENTADO", 0)).isEqualTo("23514");
            assertThat(tentarInserir(c, "INVENTADA", "EM_REVISAO", 0)).isEqualTo("23514");
        }
    }

    @Test
    void checkDeTrechosExigePublicadoComTrechosEOutrosSemTrechos() throws SQLException {
        try (Connection c = comoApp()) {
            assertThat(tentarInserir(c, "CURSO", "PUBLICADO", 0)).isEqualTo("23514");
            assertThat(tentarInserir(c, "CURSO", "EM_REVISAO", 3)).isEqualTo("23514");
            assertThat(tentarInserir(c, "CURSO", "PROCESSANDO", 1)).isEqualTo("23514");
            assertThat(tentarInserir(c, "CURSO", "PUBLICADO", 3)).isNull();
        }
    }

    @Test
    void hashDeConteudoEhUnico() throws SQLException {
        try (Connection c = comoApp()) {
            assertThat(tentarInserir(c, "CURSO", "EM_REVISAO", 0)).isNull();
            assertThat(tentarInserir(c, "CURSO", "EM_REVISAO", 0)).isEqualTo("23505");
        }
    }

    /** Insere um documento com o hash fixo e devolve o SQLState do erro, ou {@code null} se deu certo. */
    private String tentarInserir(Connection c, String categoria, String status, int chunks) {
        String sql = "INSERT INTO documento (id, titulo, conteudo, fonte, categoria, status, hash_conteudo, "
                + "quantidade_chunks, modelo_embedding, cadastrado_por, cadastrado_em) "
                + "VALUES (?, 't', 'c', 'f', ?, ?, ?, ?, 'm', 'g', now())";
        String hash = (status + categoria + chunks).equals("EM_REVISAOCURSO0") ? HASH_A : UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, categoria);
            ps.setString(3, status);
            ps.setString(4, hash);
            ps.setInt(5, chunks);
            ps.executeUpdate();
            return null;
        } catch (SQLException e) {
            return e.getSQLState();
        }
    }
}
