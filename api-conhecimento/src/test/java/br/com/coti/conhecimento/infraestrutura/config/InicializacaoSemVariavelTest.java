package br.com.coti.conhecimento.infraestrutura.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import br.com.coti.conhecimento.ApiConhecimentoApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * A subida deve falhar, citando o nome da variável (nunca o valor), quando uma variável obrigatória
 * está ausente ou em branco. Os argumentos de linha de comando têm precedência sobre o ambiente do SO.
 */
class InicializacaoSemVariavelTest {

    private static final Map<String, String> OBRIGATORIAS = Map.of(
            "OPENAI_API_KEY", "valor-secreto-openai",
            "CONHECIMENTO_ADMIN_API_KEY", "valor-secreto-admin",
            "MONITORAMENTO_API_KEY", "valor-secreto-monitoramento");

    @ParameterizedTest
    @ValueSource(strings = {"OPENAI_API_KEY", "CONHECIMENTO_ADMIN_API_KEY", "MONITORAMENTO_API_KEY"})
    void deveFalharCitandoAVariavelQuandoEstaEmBranco(String emBranco) {
        List<String> argumentos = argumentosBase();
        OBRIGATORIAS.forEach((nome, valor) -> argumentos.add("--" + nome + "=" + (nome.equals(emBranco) ? "" : valor)));

        String mensagens = falhaDaSubida(argumentos);

        assertThat(mensagens).contains(emBranco);
        assertSemValoresSecretos(mensagens);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPENAI_API_KEY", "CONHECIMENTO_ADMIN_API_KEY", "MONITORAMENTO_API_KEY"})
    void deveFalharCitandoAVariavelQuandoEstaAusente(String ausente) {
        assumeThat(System.getenv(ausente)).as("variável definida no ambiente do SO").isNull();
        List<String> argumentos = argumentosBase();
        OBRIGATORIAS.forEach((nome, valor) -> {
            if (!nome.equals(ausente)) {
                argumentos.add("--" + nome + "=" + valor);
            }
        });

        String mensagens = falhaDaSubida(argumentos);

        assertThat(mensagens).contains(ausente);
        assertSemValoresSecretos(mensagens);
    }

    private static List<String> argumentosBase() {
        List<String> argumentos = new ArrayList<>();
        argumentos.add("--server.port=0");
        argumentos.add("--spring.main.banner-mode=off");
        // O banco não é o assunto deste teste: variáveis falsas e sem migração, para que a falha venha só da variável testada.
        argumentos.add("--spring.flyway.enabled=false");
        argumentos.add("--CONHECIMENTO_DB_URL=jdbc:postgresql://localhost:1/inexistente");
        argumentos.add("--CONHECIMENTO_DB_USER=usuario-falso");
        argumentos.add("--CONHECIMENTO_DB_PASSWORD=senha-falsa-de-teste");
        argumentos.add("--CONHECIMENTO_FLYWAY_USER=dono-falso");
        argumentos.add("--CONHECIMENTO_FLYWAY_PASSWORD=senha-falsa-de-teste");
        return argumentos;
    }

    private static String falhaDaSubida(List<String> argumentos) {
        Throwable[] capturada = new Throwable[1];
        assertThatThrownBy(() -> {
            try {
                new SpringApplicationBuilder(ApiConhecimentoApplication.class)
                        .web(WebApplicationType.SERVLET)
                        .run(argumentos.toArray(String[]::new))
                        .close();
            } catch (Throwable t) {
                capturada[0] = t;
                throw t;
            }
        }).isInstanceOf(Exception.class);
        StringBuilder mensagens = new StringBuilder();
        for (Throwable t = capturada[0]; t != null; t = t.getCause() == t ? null : t.getCause()) {
            mensagens.append(t.getMessage()).append('\n');
        }
        return mensagens.toString();
    }

    private static void assertSemValoresSecretos(String mensagens) {
        OBRIGATORIAS.values().forEach(valor -> assertThat(mensagens).doesNotContain(valor));
    }
}
