package br.com.coti.atendimentos.infraestrutura.mcp;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.atendimentos.suporte.ChavesTeste;
import br.com.coti.atendimentos.suporte.TesteComServidorMcp;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Saúde da dependência {@code api-conhecimento} (contrato, seção 5.2): UP com o servidor MCP e a ferramenta
 * presentes; DEGRADADO (HTTP 200) quando falta a ferramenta, o servidor responde com erro ou demora; liveness e
 * readiness nunca dependem dele. Os detalhes não expõem URL, host, porta nem credencial.
 */
class HealthDegradadoIT extends TesteComServidorMcp {

    @Autowired
    private MockMvc mvc;

    private org.springframework.test.web.servlet.ResultActions saudeCompleta() throws Exception {
        return mvc.perform(get("/actuator/health").header("X-Api-Key", ChavesTeste.MONITORAMENTO));
    }

    @Test
    void comOServidorMcpEAFerramentaOStatusEUp() throws Exception {
        saudeCompleta().andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.conhecimentoMcp.status").value("UP"));
    }

    @Test
    void semAFerramentaDeBuscaFicaDegradadoComHttp200() throws Exception {
        SERVIDOR.semAFerramenta();

        saudeCompleta().andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADADO"))
                .andExpect(jsonPath("$.components.conhecimentoMcp.status").value("DEGRADADO"))
                .andExpect(jsonPath("$.components.conhecimentoMcp.details.codigo").value("FERRAMENTA_AUSENTE"));
    }

    @Test
    void servidorComErroHttpFicaDegradadoComHttp200() throws Exception {
        SERVIDOR.comStatusHttpEmTudo(500);

        saudeCompleta().andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADADO"))
                .andExpect(jsonPath("$.components.conhecimentoMcp.details.codigo").value("BASE_CONHECIMENTO_INDISPONIVEL"));
    }

    @Test
    void chaveDeServicoRecusadaTambemFicaDegradado() throws Exception {
        SERVIDOR.exigindoBearer("outra-chave");

        saudeCompleta().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEGRADADO"));
    }

    @Test
    void servidorIndisponivelCom503FicaDegradadoRapido() throws Exception {
        SERVIDOR.comStatusHttpEmTudo(503);
        long inicio = System.nanoTime();

        saudeCompleta().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEGRADADO"));

        org.assertj.core.api.Assertions.assertThat(Duration.ofNanos(System.nanoTime() - inicio))
                .isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void oStatusPublicoMostraSoODegradadoSemComponentes() throws Exception {
        SERVIDOR.semAFerramenta();

        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADADO"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void livenessEReadinessContinuamUpComABaseDegradada() throws Exception {
        SERVIDOR.comStatusHttpEmTudo(500);

        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void osDetalhesNuncaExpoemUrlHostPortaNemChave() throws Exception {
        SERVIDOR.comStatusHttpEmTudo(500);

        saudeCompleta().andExpect(status().isOk())
                .andExpect(content().string(not(containsString("127.0.0.1"))))
                .andExpect(content().string(not(containsString(String.valueOf(SERVIDOR.porta())))))
                .andExpect(content().string(not(containsString("http"))))
                .andExpect(content().string(not(containsString(ChavesTeste.MCP))));
    }

    @Test
    void voltaParaUpQuandoOServidorSeRecupera() throws Exception {
        SERVIDOR.semAFerramenta();
        saudeCompleta().andExpect(jsonPath("$.status").value("DEGRADADO"));

        SERVIDOR.reiniciar().exigindoBearer(ChavesTeste.MCP);

        saudeCompleta().andExpect(jsonPath("$.status").value("UP"));
    }
}
