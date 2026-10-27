package br.com.coti.conhecimento.infraestrutura.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.conhecimento.suporte.ChavesTeste;
import br.com.coti.conhecimento.suporte.EmbeddingModelFalso;
import br.com.coti.conhecimento.suporte.TesteWeb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A cadeia de {@code /mcp} aceita só a chave de serviço em {@code Authorization: Bearer}. As demais chaves e as
 * outras formas de enviar a credencial recebem 401 antes de qualquer processamento do protocolo MCP, e a chave
 * de serviço não abre os endpoints REST.
 */
@TesteWeb
class SegurancaMcpIT {

    private static final String INICIALIZAR = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
            "capabilities":{},"clientInfo":{"name":"teste","version":"1"}}}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EmbeddingModelFalso embeddings;

    @BeforeEach
    void limpar() {
        embeddings.reiniciar();
    }

    private org.springframework.test.web.servlet.ResultActions chamar(String cabecalho, String valor) throws Exception {
        var requisicao = post("/mcp").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(INICIALIZAR);
        if (cabecalho != null) {
            requisicao.header(cabecalho, valor);
        }
        return mvc.perform(requisicao);
    }

    @Test
    void semCredencialDevolve401() throws Exception {
        chamar(null, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void chaveErradaDevolve401() throws Exception {
        chamar("Authorization", "Bearer chave-errada").andExpect(status().isUnauthorized());
    }

    @Test
    void chaveAdministrativaComoBearerDevolve401() throws Exception {
        chamar("Authorization", "Bearer " + ChavesTeste.ADMIN).andExpect(status().isUnauthorized());
    }

    @Test
    void chaveDeMonitoramentoComoBearerDevolve401() throws Exception {
        chamar("Authorization", "Bearer " + ChavesTeste.MONITORAMENTO).andExpect(status().isUnauthorized());
    }

    @Test
    void chaveAdministrativaNoCabecalhoDaApiRestDevolve401() throws Exception {
        chamar("X-Api-Key", ChavesTeste.ADMIN).andExpect(status().isUnauthorized());
    }

    @Test
    void chaveDeServicoNoCabecalhoDaApiRestDevolve401() throws Exception {
        chamar("X-Api-Key", ChavesTeste.MCP).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic " + ChavesTeste.MCP, ChavesTeste.MCP, "Bearer", "Bearer ", "Token " + ChavesTeste.MCP})
    void outrosEsquemasEChaveSemEsquemaDevolvem401(String valor) throws Exception {
        chamar("Authorization", valor).andExpect(status().isUnauthorized());
    }

    @Test
    void chaveDeServicoComEsquemaEmMinusculasEhAceita() throws Exception {
        MvcResult resultado = chamar("Authorization", "bearer " + ChavesTeste.MCP).andReturn();

        assertThat(resultado.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void chaveDeServicoCorretaPassaAAutenticacao() throws Exception {
        MvcResult resultado = chamar("Authorization", "Bearer " + ChavesTeste.MCP).andReturn();

        assertThat(resultado.getResponse().getStatus()).as("a autenticação passou; o resto é do protocolo")
                .isNotIn(401, 403);
    }

    @Test
    void outrosMetodosHttpSemCredencialTambemDevolvem401() throws Exception {
        mvc.perform(get("/mcp").accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/mcp")).andExpect(status().isUnauthorized());
    }

    @Test
    void subcaminhosDoMcpTambemExigemACredencial() throws Exception {
        mvc.perform(post("/mcp/qualquer").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void respostaDe401NaoRevelaAChaveNemDetalhes() throws Exception {
        chamar("Authorization", "Bearer " + ChavesTeste.ADMIN).andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString(ChavesTeste.ADMIN))))
                .andExpect(content().string(not(containsString(ChavesTeste.MCP))));
    }

    @Test
    void asRecusasNaoChegamAoModeloDeEmbedding() throws Exception {
        chamar(null, null).andExpect(status().isUnauthorized());
        chamar("Authorization", "Bearer " + ChavesTeste.ADMIN).andExpect(status().isUnauthorized());

        assertThat(embeddings.chamadas()).isZero();
    }

    // ---- a chave de serviço não serve para mais nada

    @Test
    void chaveDeServicoNaoAbreOsEndpointsRest() throws Exception {
        mvc.perform(get("/api/conhecimentos").header("Authorization", "Bearer " + ChavesTeste.MCP))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/conhecimentos").header("X-Api-Key", ChavesTeste.MCP))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/conhecimentos/diagnostico/busca").header("X-Api-Key", ChavesTeste.MCP)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pergunta\":\"qualquer pergunta\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chaveDeServicoNaoAbreOActuatorSensivel() throws Exception {
        mvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + ChavesTeste.MCP))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").header("X-Api-Key", ChavesTeste.MCP))
                .andExpect(status().isUnauthorized());
    }
}
