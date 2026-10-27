package br.com.coti.atendimentos.suporte;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Ambiente dos testes entre os dois serviços reais (T053): PostgreSQL com pgvector em Testcontainers, a OpenAI
 * falsa de embeddings e a {@code api-conhecimento} de verdade, o mesmo JAR empacotado, executado como processo à
 * parte com o papel de aplicação só DML. A {@code api-atendimentos} sobe no próprio teste e fala com ela por MCP.
 *
 * <p>Sobe uma vez por JVM. Exige o Docker ativo e o JAR da {@code api-conhecimento} construído (o
 * {@code ./mvnw verify} da raiz o constrói antes de testar este módulo). Sem o JAR, os testes são ignorados com a
 * causa na mensagem; nunca passam em silêncio.
 */
public final class AmbienteIntegrado {

    private static final String IMAGEM_POSTGRES = "pgvector/pgvector:0.8.7-pg18-trixie";
    private static final String BANCO = "coti_conhecimento";

    private static AmbienteIntegrado instancia;

    private final String chaveMcp = aleatoria();
    private final String chaveAdmin = aleatoria();
    private final OpenAiFalsa openAi = OpenAiFalsa.iniciar();
    private final HttpClient http = HttpClient.newHttpClient();
    private final Path registro;
    private PostgreSQLContainer postgres;
    private Process conhecimento;
    private int portaConhecimento;

    private AmbienteIntegrado() {
        try {
            registro = Files.createTempFile("api-conhecimento-integracao-", ".log");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Sobe o ambiente na primeira chamada; ignora o teste se o JAR da api-conhecimento não existir. */
    public static synchronized AmbienteIntegrado obter() {
        if (instancia == null) {
            Path jar = localizarJar();
            assumeTrue(jar != null, "JAR da api-conhecimento não encontrado em ../api-conhecimento/target; rode "
                    + "./mvnw verify na raiz para construí-lo antes de testar a api-atendimentos");
            AmbienteIntegrado novo = new AmbienteIntegrado();
            novo.iniciar(jar);
            Runtime.getRuntime().addShutdownHook(new Thread(novo::encerrar));
            instancia = novo;
        }
        return instancia;
    }

    public String urlConhecimento() {
        return "http://127.0.0.1:" + portaConhecimento;
    }

    public String chaveMcp() {
        return chaveMcp;
    }

    public OpenAiFalsa openAi() {
        return openAi;
    }

    // ---- chamadas diretas à api-conhecimento

    /** Cadastra um documento pela API administrativa da api-conhecimento; devolve o status HTTP. */
    public int cadastrarConhecimento(String titulo, String conteudo, String fonte, String categoria) {
        String corpo = "{\"titulo\":" + literal(titulo) + ",\"conteudo\":" + literal(conteudo) + ",\"fonte\":"
                + literal(fonte) + ",\"categoria\":\"" + categoria + "\"}";
        return enviar(HttpRequest.newBuilder(URI.create(urlConhecimento() + "/api/conhecimentos"))
                .header("X-Api-Key", chaveAdmin).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8)).build());
    }

    /** Envia {@code POST /mcp} com a credencial informada; devolve o status HTTP (401 antes de qualquer MCP). */
    public int chamarMcpComBearer(String chave) {
        return enviar(HttpRequest.newBuilder(URI.create(urlConhecimento() + "/mcp"))
                .header("Authorization", "Bearer " + chave).header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")).build());
    }

    private int enviar(HttpRequest requisicao) {
        try {
            return http.send(requisicao, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Falha ao chamar a api-conhecimento.", e);
        }
    }

    private static String literal(String texto) {
        return "\"" + texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    // ---- ciclo de vida

    private void iniciar(Path jar) {
        String senhaSuper = aleatoria();
        String senhaDono = aleatoria();
        String senhaApp = aleatoria();
        postgres = new PostgreSQLContainer(DockerImageName.parse(IMAGEM_POSTGRES).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("postgres").withUsername("postgres").withPassword(senhaSuper)
                .withEnv("CONHECIMENTO_FLYWAY_USER", "coti_owner").withEnv("CONHECIMENTO_FLYWAY_PASSWORD", senhaDono)
                .withEnv("CONHECIMENTO_DB_USER", "coti_app").withEnv("CONHECIMENTO_DB_PASSWORD", senhaApp)
                .withCopyFileToContainer(MountableFile.forHostPath(scriptDeRoles(), 0755),
                        "/docker-entrypoint-initdb.d/01-roles.sh");
        postgres.start();
        portaConhecimento = portaLivre();

        ProcessBuilder processo = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString());
        Map<String, String> ambiente = processo.environment();
        ambiente.put("SERVER_PORT", String.valueOf(portaConhecimento));
        ambiente.put("OPENAI_API_KEY", "chave-falsa-de-integracao"); // sobrescreve a chave real do ambiente, se houver
        ambiente.put("SPRING_AI_OPENAI_BASE_URL", openAi.urlBase());
        ambiente.put("CONHECIMENTO_ADMIN_API_KEY", chaveAdmin);
        ambiente.put("MONITORAMENTO_API_KEY", aleatoria());
        ambiente.put("CONHECIMENTO_MCP_API_KEY", chaveMcp);
        ambiente.put("CONHECIMENTO_DB_URL", "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(5432) + "/" + BANCO);
        ambiente.put("CONHECIMENTO_DB_USER", "coti_app");
        ambiente.put("CONHECIMENTO_DB_PASSWORD", senhaApp);
        ambiente.put("CONHECIMENTO_FLYWAY_USER", "coti_owner");
        ambiente.put("CONHECIMENTO_FLYWAY_PASSWORD", senhaDono);
        processo.redirectErrorStream(true).redirectOutput(registro.toFile());
        try {
            conhecimento = processo.start();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível iniciar a api-conhecimento.", e);
        }
        aguardarProntidao();
    }

    private void aguardarProntidao() {
        long limite = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        HttpRequest saude = HttpRequest.newBuilder(URI.create(urlConhecimento() + "/actuator/health/readiness"))
                .timeout(Duration.ofSeconds(2)).GET().build();
        while (System.nanoTime() < limite) {
            if (!conhecimento.isAlive()) {
                throw new IllegalStateException("A api-conhecimento terminou ao subir. Fim do log:\n" + fimDoRegistro());
            }
            try {
                if (http.send(saude, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    return;
                }
            } catch (IOException e) {
                // ainda subindo
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("A api-conhecimento não ficou pronta em 120 s. Fim do log:\n" + fimDoRegistro());
    }

    private String fimDoRegistro() {
        try {
            List<String> linhas = Files.readAllLines(registro, StandardCharsets.UTF_8);
            return String.join("\n", linhas.subList(Math.max(0, linhas.size() - 25), linhas.size()));
        } catch (IOException e) {
            return "(registro ilegível)";
        }
    }

    private void encerrar() {
        if (conhecimento != null) {
            conhecimento.destroy();
        }
        openAi.close();
        if (postgres != null) {
            postgres.stop();
        }
        try {
            Files.deleteIfExists(registro);
        } catch (IOException e) {
            // arquivo temporário: o sistema limpa depois
        }
    }

    private static Path localizarJar() {
        Path pasta = Path.of("..", "api-conhecimento", "target").toAbsolutePath().normalize();
        if (!Files.isDirectory(pasta)) {
            return null;
        }
        try (Stream<Path> arquivos = Files.list(pasta)) {
            return arquivos.filter(p -> p.getFileName().toString().matches("api-conhecimento-.*\\.jar"))
                    .filter(p -> !p.getFileName().toString().endsWith(".original"))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
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

    private static int portaLivre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String aleatoria() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
