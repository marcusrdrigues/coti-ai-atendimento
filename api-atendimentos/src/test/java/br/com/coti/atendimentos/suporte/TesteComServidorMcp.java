package br.com.coti.atendimentos.suporte;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base dos testes que precisam do servidor MCP de teste no ar: aplicação completa com MockMvc, chaves falsas,
 * tempos curtos (tempo limite do MCP de 400 ms, atraso entre tentativas de 1 ms, verificação de saúde sem cache)
 * e o {@link ServidorMcpDeTeste} exigindo a chave de serviço. Nenhuma chamada à OpenAI é feita.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "OPENAI_API_KEY=" + ChavesTeste.OPENAI,
        "OPENAI_CHAT_MODEL=" + ChavesTeste.MODELO_CHAT,
        "MONITORAMENTO_API_KEY=" + ChavesTeste.MONITORAMENTO,
        "CONHECIMENTO_MCP_API_KEY=" + ChavesTeste.MCP,
        "ATENDIMENTO_MENSAGEM_SEM_EVIDENCIA=" + ChavesTeste.SEM_EVIDENCIA,
        "ATENDIMENTO_CANAL_HUMANO=" + ChavesTeste.CANAL_HUMANO,
        "coti.atendimentos.resiliencia.mcp-tempo-limite=400ms",
        "coti.atendimentos.resiliencia.atraso-inicial=1ms",
        "coti.atendimentos.resiliencia.atraso-maximo=5ms",
        "coti.atendimentos.resiliencia.jitter=0ms",
        "coti.atendimentos.resiliencia.saude-tempo-limite=1s",
        "coti.atendimentos.resiliencia.saude-validade=1ms",
        "spring.ai.mcp.client.request-timeout=2s"
})
@AutoConfigureMockMvc
public abstract class TesteComServidorMcp {

    protected static final ServidorMcpDeTeste SERVIDOR = ServidorMcpDeTeste.iniciar();

    @DynamicPropertySource
    static void enderecoDoServidorMcp(DynamicPropertyRegistry registro) {
        registro.add("CONHECIMENTO_MCP_URL", SERVIDOR::url);
    }

    @BeforeEach
    void restaurarServidorMcp() {
        SERVIDOR.reiniciar().exigindoBearer(ChavesTeste.MCP);
    }
}
