package br.com.coti.atendimentos.suporte;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base dos testes que precisam do chat da OpenAI simulado: aplicação completa com MockMvc, chaves falsas, tempos
 * curtos (limite de 1,5 s por tentativa do modelo, atraso entre tentativas de 1 ms) e a {@link OpenAiFalsa}
 * respondendo no lugar da OpenAI. Nenhuma chamada sai da JVM. O servidor MCP de teste também está no ar.
 *
 * <p>A primeira chamada de uma JVM carrega classes e abre conexões (MCP e HTTP), e pode passar do limite de 1,5 s.
 * Por isso a base faz um aquecimento uma vez, fora de qualquer asserção: nenhum teste depende de partida a frio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "OPENAI_API_KEY=" + ChavesTeste.OPENAI,
        "OPENAI_CHAT_MODEL=" + ChavesTeste.MODELO_CHAT,
        "MONITORAMENTO_API_KEY=" + ChavesTeste.MONITORAMENTO,
        "CONHECIMENTO_MCP_API_KEY=" + ChavesTeste.MCP,
        "ATENDIMENTO_MENSAGEM_SEM_EVIDENCIA=" + ChavesTeste.SEM_EVIDENCIA,
        "ATENDIMENTO_CANAL_HUMANO=" + ChavesTeste.CANAL_HUMANO,
        "coti.atendimentos.limites-requisicao.perguntas-por-minuto-por-ip=100000",
        "coti.atendimentos.resiliencia.mcp-tempo-limite=1500ms",
        "coti.atendimentos.resiliencia.chat-tempo-limite=1500ms",
        "coti.atendimentos.resiliencia.atraso-inicial=1ms",
        "coti.atendimentos.resiliencia.atraso-maximo=5ms",
        "coti.atendimentos.resiliencia.jitter=0ms",
        "coti.atendimentos.resiliencia.saude-validade=1ms",
        "spring.ai.mcp.client.request-timeout=3s"
})
@AutoConfigureMockMvc
public abstract class TesteComOpenAiFalsa {

    protected static final OpenAiFalsa OPENAI = OpenAiFalsa.iniciar();
    protected static final ServidorMcpDeTeste MCP = ServidorMcpDeTeste.iniciar();

    private static boolean aquecido;

    @Autowired
    private MockMvc mvcDeAquecimento;

    @DynamicPropertySource
    static void enderecos(DynamicPropertyRegistry registro) {
        registro.add("spring.ai.openai.base-url", OPENAI::urlBase);
        registro.add("CONHECIMENTO_MCP_URL", MCP::url);
    }

    @BeforeEach
    void restaurarServidores() throws Exception {
        restaurar();
        if (!aquecido) {
            MCP.respondendoComTrechos(ServidorMcpDeTeste.TRECHO_1);
            OPENAI.chatPadrao(OpenAiFalsa.RespostaChat.ok(OpenAiFalsa.saida("Aquecimento.", false)));
            for (int tentativa = 0; tentativa < 3 && !aquecido; tentativa++) {
                aquecido = mvcDeAquecimento.perform(post("/api/atendimentos/perguntar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pergunta\":\"aquecimento\"}"))
                        .andReturn().getResponse().getStatus() == 200;
            }
            restaurar();
        }
    }

    private static void restaurar() {
        OPENAI.limparChat();
        OPENAI.limpar();
        MCP.reiniciar().exigindoBearer(ChavesTeste.MCP);
    }
}
