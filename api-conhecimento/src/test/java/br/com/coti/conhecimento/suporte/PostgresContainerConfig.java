package br.com.coti.conhecimento.suporte;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL 18 com pgvector em Testcontainers, compartilhado por toda a execução dos testes (um contêiner por
 * JVM, encerrado pelo Ryuk). Os papéis são criados pelo mesmo script do Docker Compose
 * ({@code docker/postgres/init/01-roles.sh}): o dono executa as migrações e a aplicação só faz DML.
 * As senhas são geradas a cada execução.
 *
 * <p>Usa propriedades dinâmicas em vez de {@code @ServiceConnection} porque a aplicação precisa de dois
 * papéis (Flyway e aplicação), e o {@code @ServiceConnection} fornece um só.
 */
public final class PostgresContainerConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String BANCO = "coti_conhecimento";
    public static final String USUARIO_DONO = "coti_owner";
    public static final String USUARIO_APP = "coti_app";

    private static final String IMAGEM = "pgvector/pgvector:0.8.7-pg18-trixie";
    private static final String SENHA_SUPER = aleatoria();
    private static final String SENHA_DONO = aleatoria();
    private static final String SENHA_APP = aleatoria();

    private static final PostgreSQLContainer POSTGRES = criar();

    static {
        POSTGRES.start();
    }

    private static PostgreSQLContainer criar() {
        return new PostgreSQLContainer(DockerImageName.parse(IMAGEM).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("postgres")
                .withUsername("postgres")
                .withPassword(SENHA_SUPER)
                .withEnv("CONHECIMENTO_FLYWAY_USER", USUARIO_DONO)
                .withEnv("CONHECIMENTO_FLYWAY_PASSWORD", SENHA_DONO)
                .withEnv("CONHECIMENTO_DB_USER", USUARIO_APP)
                .withEnv("CONHECIMENTO_DB_PASSWORD", SENHA_APP)
                .withCopyFileToContainer(MountableFile.forHostPath(scriptDeRoles(), 0755),
                        "/docker-entrypoint-initdb.d/01-roles.sh");
    }

    private static Path scriptDeRoles() {
        for (String candidato : new String[] {"../docker/postgres/init/01-roles.sh", "docker/postgres/init/01-roles.sh"}) {
            Path caminho = Path.of(candidato).toAbsolutePath().normalize();
            if (Files.exists(caminho)) {
                return caminho;
            }
        }
        throw new IllegalStateException("docker/postgres/init/01-roles.sh não encontrado a partir de "
                + Path.of("").toAbsolutePath());
    }

    private static String aleatoria() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public void initialize(ConfigurableApplicationContext contexto) {
        TestPropertyValues.of(
                "spring.datasource.url=" + urlJdbc(),
                "spring.datasource.username=" + USUARIO_APP,
                "spring.datasource.password=" + SENHA_APP,
                "spring.flyway.user=" + USUARIO_DONO,
                "spring.flyway.password=" + SENHA_DONO).applyTo(contexto);
    }

    public static String urlJdbc() {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + BANCO;
    }

    public static String senhaDono() {
        return SENHA_DONO;
    }

    public static String senhaApp() {
        return SENHA_APP;
    }
}
