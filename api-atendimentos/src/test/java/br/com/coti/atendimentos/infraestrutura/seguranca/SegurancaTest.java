package br.com.coti.atendimentos.infraestrutura.seguranca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.atendimentos.suporte.ChavesTeste;
import br.com.coti.atendimentos.suporte.TesteWeb;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@TesteWeb
class SegurancaTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rotaPublicaDePerguntasNaoExigeCredencial() throws Exception {
        // a segurança deixa passar (não é 401): sem corpo JSON o controlador responde 415 do contrato
        mvc.perform(post("/api/atendimentos/perguntar")).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void qualquerOutraRotaExige401ComProblemDetailsEIdDeCorrelacao() throws Exception {
        mvc.perform(get("/api/atendimentos"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"))
                .andExpect(jsonPath("$.type").value("urn:coti-ai:problema:nao-autenticado"))
                .andExpect(jsonPath("$.correlacaoId").isNotEmpty());
        mvc.perform(get("/qualquer-coisa")).andExpect(status().isUnauthorized());
        mvc.perform(get("/mcp")).andExpect(status().isUnauthorized());
    }

    @Test
    void chaveDeMonitoramentoNaoAbrePortasDaApi() throws Exception {
        mvc.perform(get("/api/atendimentos").header("X-Api-Key", ChavesTeste.MONITORAMENTO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorRecusaChaveIncorreta() throws Exception {
        mvc.perform(get("/actuator/metrics").header("X-Api-Key", "chave-errada"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void naoDeveEmitirCookiesNemExigirCsrf() throws Exception {
        mvc.perform(post("/api/atendimentos/perguntar"))
                .andExpect(status().is4xxClientError())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.codigo").value("TIPO_MIDIA_NAO_SUPORTADO"));
    }

    @Test
    void deveEnviarCabecalhosDeSegurancaPadrao() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Cache-Control"));
    }
}
