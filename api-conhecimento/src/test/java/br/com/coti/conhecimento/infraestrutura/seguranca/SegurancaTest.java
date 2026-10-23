package br.com.coti.conhecimento.infraestrutura.seguranca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.conhecimento.suporte.ChavesTeste;
import br.com.coti.conhecimento.suporte.TesteWeb;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@TesteWeb
class SegurancaTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void deveExigirChaveEm401ComProblemDetailsEIdDeCorrelacao() throws Exception {
        mvc.perform(get("/api/conhecimentos"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"))
                .andExpect(jsonPath("$.type").value("urn:coti-ai:problema:nao-autenticado"))
                .andExpect(jsonPath("$.correlacaoId").isNotEmpty());
    }

    @Test
    void deveRecusarChaveIncorreta() throws Exception {
        mvc.perform(get("/api/conhecimentos").header("X-Api-Key", "chave-errada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void deveRecusarAChaveDeMonitoramentoNaApi() throws Exception {
        mvc.perform(get("/api/conhecimentos").header("X-Api-Key", ChavesTeste.MONITORAMENTO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRecusarAChaveAdministrativaNoActuator() throws Exception {
        mvc.perform(get("/actuator/metrics").header("X-Api-Key", ChavesTeste.ADMIN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveAutenticarComAChaveAdministrativa() throws Exception {
        // a rota ainda não existe (404), o que prova que a segurança deixou passar
        mvc.perform(get("/api/conhecimentos").header("X-Api-Key", ChavesTeste.ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveNegarPorPadraoQualquerOutraRota() throws Exception {
        mvc.perform(get("/qualquer-coisa")).andExpect(status().isUnauthorized());
        mvc.perform(get("/qualquer-coisa").header("X-Api-Key", ChavesTeste.ADMIN))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/mcp")).andExpect(status().isUnauthorized());
    }

    @Test
    void deveManterAPoliticaSemSessaoENaoEmitirCookies() throws Exception {
        mvc.perform(get("/api/conhecimentos").header("X-Api-Key", ChavesTeste.ADMIN))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void deveIgnorarCsrfEmMetodosQueAlteramEstado() throws Exception {
        // sem token CSRF: se o CSRF estivesse ativo seria 403; o esperado aqui é 404 (rota inexistente)
        mvc.perform(post("/api/conhecimentos").header("X-Api-Key", ChavesTeste.ADMIN))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void deveEnviarCabecalhosDeSegurancaPadrao() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Cache-Control"));
    }
}
