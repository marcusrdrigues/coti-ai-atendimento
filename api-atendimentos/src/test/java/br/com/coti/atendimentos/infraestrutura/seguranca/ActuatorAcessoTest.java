package br.com.coti.atendimentos.infraestrutura.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.atendimentos.suporte.ChavesTeste;
import br.com.coti.atendimentos.suporte.ServidorMcpDeTeste;
import br.com.coti.atendimentos.suporte.TesteWeb;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@TesteWeb
class ActuatorAcessoTest {

    /** Servidor MCP de teste no ar: com ele o status agregado é UP; sem ele seria DEGRADADO (HealthDegradadoIT). */
    static final ServidorMcpDeTeste SERVIDOR_MCP = ServidorMcpDeTeste.iniciar();

    @DynamicPropertySource
    static void servidorMcp(DynamicPropertyRegistry registro) {
        registro.add("CONHECIMENTO_MCP_URL", SERVIDOR_MCP::url);
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void healthPublicoMostraSomenteOStatus() throws Exception {
        String corpo = mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = JsonMapper.builder().build().readTree(corpo);
        assertThat(json.propertyNames()).isSubsetOf("status", "groups"); // sem componentes nem detalhes
        assertThat(json.get("status").asString()).isEqualTo("UP");
    }

    @Test
    void livenessEReadinessSaoPublicos() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthComChaveDeMonitoramentoMostraComponentes() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Api-Key", ChavesTeste.MONITORAMENTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").exists());
    }

    @Test
    void infoEPublico() throws Exception {
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    @Test
    void metricasExigemAChaveDeMonitoramento() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        mvc.perform(get("/actuator/metrics").header("X-Api-Key", ChavesTeste.MONITORAMENTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    @Test
    void endpointsSensiveisNaoSaoExpostos() throws Exception {
        for (String endpoint : new String[] {"env", "configprops", "beans", "heapdump", "threaddump", "mappings",
                "loggers", "shutdown"}) {
            mvc.perform(get("/actuator/" + endpoint).header("X-Api-Key", ChavesTeste.MONITORAMENTO))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/actuator/" + endpoint)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void indiceDoActuatorListaApenasOsEndpointsExpostos() throws Exception {
        mvc.perform(get("/actuator").header("X-Api-Key", ChavesTeste.MONITORAMENTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links", hasKey("health")))
                .andExpect(jsonPath("$._links", hasKey("metrics")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/env"))));
    }
}
