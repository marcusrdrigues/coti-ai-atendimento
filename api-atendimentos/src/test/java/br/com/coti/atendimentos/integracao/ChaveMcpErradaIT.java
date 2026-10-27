package br.com.coti.atendimentos.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.atendimentos.suporte.AmbienteIntegrado;
import br.com.coti.atendimentos.suporte.ChavesTeste;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T053: a {@code api-atendimentos} configurada com a chave MCP errada recebe 401 da {@code api-conhecimento} real e
 * responde ao aluno com 503 {@code BASE_CONHECIMENTO_INDISPONIVEL}, sem repetir a chamada nem vazar a causa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "OPENAI_API_KEY=" + ChavesTeste.OPENAI,
        "OPENAI_CHAT_MODEL=" + ChavesTeste.MODELO_CHAT,
        "MONITORAMENTO_API_KEY=" + ChavesTeste.MONITORAMENTO,
        "ATENDIMENTO_MENSAGEM_SEM_EVIDENCIA=" + ChavesTeste.SEM_EVIDENCIA,
        "ATENDIMENTO_CANAL_HUMANO=" + ChavesTeste.CANAL_HUMANO,
        "CONHECIMENTO_MCP_API_KEY=chave-mcp-errada"
})
@AutoConfigureMockMvc
class ChaveMcpErradaIT {

    private static AmbienteIntegrado ambiente;

    @BeforeAll
    static void subirAmbiente() {
        ambiente = AmbienteIntegrado.obter();
    }

    @DynamicPropertySource
    static void conectarAoConhecimento(DynamicPropertyRegistry registro) {
        registro.add("CONHECIMENTO_MCP_URL", () -> ambiente.urlConhecimento());
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void aApiConhecimentoRealRecusaAChaveErradaComOuSemBearerEAChaveCertaPassa() {
        assertThat(ambiente.chamarMcpComBearer("chave-mcp-errada")).isEqualTo(401);
        assertThat(ambiente.chamarMcpComBearer("")).isEqualTo(401);
        assertThat(ambiente.chamarMcpComBearer(ambiente.chaveMcp())).isNotIn(401, 403);
    }

    @Test
    void oAlunoRecebe503DoContratoSemVazarACausa() throws Exception {
        ambiente.openAi().limpar();

        mvc.perform(post("/api/atendimentos/perguntar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\":\"Quais são as modalidades do curso de Angular?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("BASE_CONHECIMENTO_INDISPONIVEL"))
                .andExpect(header().string("Retry-After", "30"));

        assertThat(ambiente.openAi().textosRecebidos()).as("a busca nem chegou a gerar embedding").isEmpty();
    }
}
