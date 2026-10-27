package br.com.coti.conhecimento.infraestrutura.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
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
        mvc.perform(get("/api/conhecimentos").header("X-Api-Key", ChavesTeste.ADMIN))
                .andExpect(status().isOk());
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
        // sem token CSRF: se o CSRF estivesse ativo seria 403; o esperado é a validação da requisição (415)
        mvc.perform(post("/api/conhecimentos").header("X-Api-Key", ChavesTeste.ADMIN))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_MIDIA_NAO_SUPORTADO"));
    }

    @Autowired
    private org.springframework.context.ApplicationContext contexto;

    @Test
    void naoCriaUsuarioEmMemoriaNemSenhaGeradaNoLog() {
        assertThat(contexto.getBeansOfType(org.springframework.security.core.userdetails.UserDetailsService.class)).isEmpty();
    }

    @Test
    void deveEnviarCabecalhosDeSegurancaPadrao() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Cache-Control"));
    }
}
