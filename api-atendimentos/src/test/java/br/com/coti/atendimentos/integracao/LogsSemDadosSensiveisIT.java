package br.com.coti.atendimentos.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import br.com.coti.atendimentos.suporte.ChavesTeste;
import br.com.coti.atendimentos.suporte.OpenAiFalsa;
import br.com.coti.atendimentos.suporte.OpenAiFalsa.RespostaChat;
import br.com.coti.atendimentos.suporte.ServidorMcpDeTeste;
import br.com.coti.atendimentos.suporte.TesteComOpenAiFalsa;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * T061: nenhum log do fluxo completo (sucesso, saída reprovada, falha do modelo) contém a pergunta do aluno, dado
 * pessoal, o texto dos trechos, o texto da resposta nem chave de API (AGENTS.md 4.4). Captura tudo que passa pelo
 * Logback, inclusive o que o Spring AI e o SDK da OpenAI escrevem, com {@code DEBUG} ligado nesses pacotes para
 * que o teste pegue também o que só aparece quando alguém aumenta o nível. A configuração
 * {@code spring.ai.chat.observations.log-prompt/log-completion} está desligada em {@code application.yml}.
 */
class LogsSemDadosSensiveisIT extends TesteComOpenAiFalsa {

    private static final String CPF = "529.982.247-25";
    private static final String EMAIL = "maria@exemplo.com.br";
    private static final String FRASE_DA_PERGUNTA = "pergunta singular do aluno sobre lógica";
    private static final String FRASE_DO_TRECHO = "frase singular do trecho sigiloso da base";
    private static final String FRASE_DA_RESPOSTA = "frase singular da resposta do modelo";

    private static final String TRECHO = """
            {"id":"cc333333-3333-4333-8333-333333333333",
             "texto":"Para o curso Java Fullstack é recomendado conhecimento básico de lógica de programação. %s.",
             "documentoId":"dd444444-4444-4444-8444-444444444444","titulo":"Curso Java — pré-requisitos",
             "fonte":"Site COTI","categoria":"PRE_REQUISITO","similaridade":0.9}""".formatted(FRASE_DO_TRECHO);

    @Autowired
    private MockMvc mvc;

    private final ListAppender<ILoggingEvent> capturados = new ListAppender<>();
    private final List<Logger> ajustados = new ArrayList<>();
    private final List<Level> niveisAnteriores = new ArrayList<>();

    @BeforeEach
    void capturarLogs() {
        capturados.start();
        Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        raiz.addAppender(capturados);
        // O SDK do MCP fica de fora de propósito: em DEBUG ele registra o JSON-RPC inteiro. O que se prova dele é a
        // configuração (teste abaixo), não que o DEBUG seja seguro.
        for (String pacote : List.of("org.springframework.ai", "com.openai", "br.com.coti")) {
            Logger logger = (Logger) LoggerFactory.getLogger(pacote);
            ajustados.add(logger);
            niveisAnteriores.add(logger.getLevel());
            logger.setLevel(Level.DEBUG);
        }
    }

    @AfterEach
    void restaurarLogs() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(capturados);
        capturados.stop();
        for (int i = 0; i < ajustados.size(); i++) {
            ajustados.get(i).setLevel(niveisAnteriores.get(i));
        }
    }

    private ResultActions perguntar(String pergunta) throws Exception {
        return mvc.perform(post("/api/atendimentos/perguntar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"pergunta\":\"" + pergunta + "\"}"));
    }

    private String tudoQueFoiLogado() {
        StringBuilder todo = new StringBuilder();
        for (ILoggingEvent evento : List.copyOf(capturados.list)) {
            todo.append(evento.getLoggerName()).append(' ').append(evento.getFormattedMessage()).append(' ')
                    .append(evento.getMDCPropertyMap()).append(' ');
            if (evento.getThrowableProxy() != null) {
                todo.append(ThrowableProxyUtil.asString(evento.getThrowableProxy()));
            }
            todo.append('\n');
        }
        return todo.toString();
    }

    private static final List<String> PROIBIDOS = List.of(CPF, EMAIL, FRASE_DA_PERGUNTA, FRASE_DO_TRECHO,
            FRASE_DA_RESPOSTA, ChavesTeste.OPENAI, ChavesTeste.MCP, ChavesTeste.MONITORAMENTO, "Bearer ");

    /** Logger e nível de cada evento que contém algo proibido, para o relatório de falha dizer quem vazou. */
    private List<String> vazamentos() {
        List<String> achados = new ArrayList<>();
        for (ILoggingEvent evento : List.copyOf(capturados.list)) {
            String texto = evento.getFormattedMessage() + (evento.getThrowableProxy() == null ? ""
                    : ThrowableProxyUtil.asString(evento.getThrowableProxy()));
            for (String proibido : PROIBIDOS) {
                if (texto.contains(proibido)) {
                    achados.add(evento.getLevel() + " " + evento.getLoggerName() + " contém \"" + proibido + "\"");
                }
            }
        }
        return achados;
    }

    private void assertNadaSensivelNoLog() {
        assertThat(tudoQueFoiLogado()).as("o fluxo precisa ter logado algo para o teste ter valor")
                .contains("Contexto montado");
        assertThat(vazamentos()).isEmpty();
    }

    @Test
    void fluxoComSucessoNaoLogaPerguntaDadoPessoalTrechoNemResposta() throws Exception {
        MCP.respondendoComTrechos(TRECHO);
        OPENAI.roteirizarChat(RespostaChat.ok(OpenAiFalsa.saida(FRASE_DA_RESPOSTA + ".", true, "T1")));

        perguntar("Meu CPF é " + CPF + " e meu e-mail é " + EMAIL + ". " + FRASE_DA_PERGUNTA + "?")
                .andExpect(status().isOk());

        assertNadaSensivelNoLog();
    }

    @Test
    void saidaReprovadaENovaTentativaNaoLogamOMotivoComTextoDoModelo() throws Exception {
        MCP.respondendoComTrechos(TRECHO);
        OPENAI.roteirizarChat(
                RespostaChat.ok(OpenAiFalsa.saida(FRASE_DA_RESPOSTA + " custa R$ 12345,67.", true, "T1")),
                RespostaChat.ok(OpenAiFalsa.saida(FRASE_DA_RESPOSTA + ".", true, "T1")));

        perguntar(FRASE_DA_PERGUNTA + "? CPF " + CPF).andExpect(status().isOk());

        assertThat(tudoQueFoiLogado()).contains("Saída do modelo reprovada");
        assertNadaSensivelNoLog();
    }

    @Test
    void falhaDoModeloNaoLogaPerguntaNemCorpoDaResposta() throws Exception {
        MCP.respondendoComTrechos(TRECHO);
        OPENAI.chatPadrao(RespostaChat.erro(500));

        perguntar(FRASE_DA_PERGUNTA + "? CPF " + CPF + " " + EMAIL).andExpect(status().isServiceUnavailable());

        assertNadaSensivelNoLog();
    }

    @Test
    void credencialRecusadaPeloProvedorNaoLogaAChave() throws Exception {
        MCP.respondendoComTrechos(TRECHO);
        OPENAI.chatPadrao(RespostaChat.erro(401));

        perguntar(FRASE_DA_PERGUNTA + "?").andExpect(status().isServiceUnavailable());

        assertThat(tudoQueFoiLogado()).contains("A OpenAI recusou a credencial");
        assertThat(vazamentos()).isEmpty();
    }

    @Test
    void oSdkDoMcpFicaEmInfoPorConfiguracaoPoisEmDebugEleLogaOJsonRpcInteiro() {
        Logger sdk = (Logger) LoggerFactory.getLogger("io.modelcontextprotocol.spec.McpSchema");

        assertThat(sdk.getEffectiveLevel().isGreaterOrEqual(Level.INFO)).isTrue();
    }

    @Test
    void semEvidenciaTambemNaoLogaAPergunta() throws Exception {
        MCP.respondendoVazio();

        perguntar(FRASE_DA_PERGUNTA + "? CPF " + CPF).andExpect(status().isOk());

        assertNadaSensivelNoLog();
    }
}
