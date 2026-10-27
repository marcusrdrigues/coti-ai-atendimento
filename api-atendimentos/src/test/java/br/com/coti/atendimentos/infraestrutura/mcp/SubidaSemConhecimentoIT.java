package br.com.coti.atendimentos.infraestrutura.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.atendimentos.aplicacao.porta.BaseConhecimentoPort;
import br.com.coti.atendimentos.dominio.Prazo;
import br.com.coti.atendimentos.dominio.excecao.BaseConhecimentoIndisponivelException;
import br.com.coti.atendimentos.suporte.ChavesTeste;
import br.com.coti.atendimentos.suporte.ServidorMcpDeTeste;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RNF07: a api-atendimentos sobe e responde com a api-conhecimento desligada. O servidor é iniciado só para
 * reservar uma porta e desligado antes de a aplicação subir, então toda conexão é recusada.
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
        "coti.atendimentos.resiliencia.jitter=0ms"
})
@AutoConfigureMockMvc
class SubidaSemConhecimentoIT {

    private static int porta;

    @DynamicPropertySource
    static void servidorDesligado(DynamicPropertyRegistry registro) {
        ServidorMcpDeTeste servidor = ServidorMcpDeTeste.iniciar();
        porta = servidor.porta();
        String url = servidor.url();
        servidor.close();
        registro.add("CONHECIMENTO_MCP_URL", () -> url);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BaseConhecimentoPort base;

    @Test
    void aAplicacaoSobeELivenessEReadinessEstaoUp() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void oStatusAgregadoFicaDegradadoComHttp200() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADADO"));
        mvc.perform(get("/actuator/health").header("X-Api-Key", ChavesTeste.MONITORAMENTO))
                .andExpect(jsonPath("$.components.conhecimentoMcp.details.codigo").value("BASE_CONHECIMENTO_INDISPONIVEL"))
                .andExpect(content().string(not(containsString(String.valueOf(porta)))));
    }

    @Test
    void aPerguntaRecebe503DoContratoComRetryAfterEACorrelacao() throws Exception {
        var resposta = mvc.perform(post("/api/atendimentos/perguntar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\":\"Quais são as modalidades do curso de Angular?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("BASE_CONHECIMENTO_INDISPONIVEL"))
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(content().string(not(containsString(String.valueOf(porta)))))
                .andExpect(content().string(not(containsString("Connect"))))
                .andReturn().getResponse();

        assertThat(resposta.getContentAsString())
                .contains("\"correlacaoId\":\"" + resposta.getHeader("X-Correlation-Id") + "\"");
    }

    @Test
    void conexaoRecusadaTemTresTentativasEViraBaseIndisponivelRapido() {
        long inicio = System.nanoTime();

        assertThatThrownBy(() -> base.buscar("pergunta válida", 5, Prazo.iniciar(Clock.systemUTC(), Duration.ofSeconds(30))))
                .isInstanceOf(BaseConhecimentoIndisponivelException.class)
                .hasMessageContaining("3 tentativas");

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(5));
    }
}
