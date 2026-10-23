package br.com.coti.atendimentos.arquitetura;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * RNF07 / RF07: a api-atendimentos não carrega driver de banco, pgvector nem acesso JDBC no classpath.
 * Se alguém adicionar essas dependências ao build, este teste falha.
 */
class IsolamentoDeDependenciasTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "org.postgresql.Driver",
            "org.springframework.ai.vectorstore.pgvector.PgVectorStore",
            "org.springframework.jdbc.core.JdbcTemplate",
            "jakarta.persistence.EntityManager",
            "org.flywaydb.core.Flyway"})
    void naoDeveTerAClasseNoClasspath(String nomeDaClasse) {
        assertThat(existe(nomeDaClasse)).as("%s não deve estar no classpath", nomeDaClasse).isFalse();
    }

    @Test
    void ochatContinuaDisponivelParaOAtendimento() {
        assertThat(existe("org.springframework.ai.chat.client.ChatClient")).isTrue();
    }

    private static boolean existe(String nomeDaClasse) {
        try {
            Class.forName(nomeDaClasse, false, IsolamentoDeDependenciasTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
