package br.com.coti.atendimentos.infraestrutura.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.atendimentos.aplicacao.ResponderPerguntaService;
import br.com.coti.atendimentos.dominio.ResultadoAtendimento;
import br.com.coti.atendimentos.suporte.ChavesTeste;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** RF06: no máximo 3 perguntas por minuto por IP neste contexto de teste (o padrão do sistema é 10). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "OPENAI_API_KEY=" + ChavesTeste.OPENAI,
        "OPENAI_CHAT_MODEL=" + ChavesTeste.MODELO_CHAT,
        "MONITORAMENTO_API_KEY=" + ChavesTeste.MONITORAMENTO,
        "CONHECIMENTO_MCP_API_KEY=" + ChavesTeste.MCP,
        "CONHECIMENTO_MCP_URL=http://127.0.0.1:1",
        "ATENDIMENTO_MENSAGEM_SEM_EVIDENCIA=" + ChavesTeste.SEM_EVIDENCIA,
        "ATENDIMENTO_CANAL_HUMANO=" + ChavesTeste.CANAL_HUMANO,
        "coti.atendimentos.limites-requisicao.perguntas-por-minuto-por-ip=3"
})
@AutoConfigureMockMvc
class LimiteRequisicoesIT {

    private static final String ROTA = "/api/atendimentos/perguntar";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ResponderPerguntaService servico;

    private ResultActions perguntar(String ip, String corpo) throws Exception {
        return mvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content(corpo)
                .with(requisicao -> {
                    requisicao.setRemoteAddr(ip);
                    return requisicao;
                }));
    }

    private static String valida() {
        return "{\"pergunta\":\"pergunta válida\"}";
    }

    private void respostaPadrao() {
        when(servico.responder(any())).thenReturn(
                ResultadoAtendimento.semEvidencia("Sem evidência.", OffsetDateTime.now()));
    }

    @Test
    void aQuartaPerguntaDoMesmoIpNoMinutoRecebe429ComRetryAfterENoFormatoDoContrato() throws Exception {
        respostaPadrao();
        for (int i = 0; i < 3; i++) {
            perguntar("10.1.1.1", valida()).andExpect(status().isOk());
        }

        var resposta = perguntar("10.1.1.1", valida()).andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("LIMITE_REQUISICOES"))
                .andExpect(jsonPath("$.type").value("urn:coti-ai:problema:limite-requisicoes"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(header().exists("Retry-After"))
                .andReturn().getResponse();

        assertThat(Long.parseLong(resposta.getHeader("Retry-After"))).isBetween(1L, 60L);
        assertThat(resposta.getContentAsString())
                .contains("\"correlacaoId\":\"" + resposta.getHeader("X-Correlation-Id") + "\"");
    }

    @Test
    void outroIpNaoEAfetado() throws Exception {
        respostaPadrao();
        for (int i = 0; i < 4; i++) {
            perguntar("10.2.2.2", valida());
        }

        perguntar("10.2.2.2", valida()).andExpect(status().isTooManyRequests());
        perguntar("10.3.3.3", valida()).andExpect(status().isOk());
    }

    @Test
    void asRequisicoesInvalidasTambemContam() throws Exception {
        respostaPadrao();
        for (int i = 0; i < 3; i++) {
            perguntar("10.4.4.4", "{\"pergunta\":\"ab\"}").andExpect(status().isBadRequest());
        }

        perguntar("10.4.4.4", valida()).andExpect(status().isTooManyRequests());
    }

    @Test
    void oCabecalhoXForwardedForEnviadoPeloClienteNaoPermiteFugirDoLimite() throws Exception {
        respostaPadrao();
        for (int i = 0; i < 3; i++) {
            perguntar("10.5.5.5", valida());
        }

        mvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content(valida())
                        .header("X-Forwarded-For", "203.0.113.77")
                        .with(requisicao -> {
                            requisicao.setRemoteAddr("10.5.5.5");
                            return requisicao;
                        }))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void oLimiteSoValeParaAsPerguntas() throws Exception {
        respostaPadrao();
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/actuator/health").with(requisicao -> {
                requisicao.setRemoteAddr("10.6.6.6");
                return requisicao;
            })).andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            perguntar("10.6.6.6", valida()).andExpect(status().isOk());
        }
    }

    @Test
    void aRecusaNaoChamaOCasoDeUsoNemVazaDetalhe() throws Exception {
        respostaPadrao();
        for (int i = 0; i < 3; i++) {
            perguntar("10.7.7.7", valida());
        }

        perguntar("10.7.7.7", valida()).andExpect(status().isTooManyRequests())
                .andExpect(content().string(not(containsString("pergunta válida"))))
                .andExpect(content().string(not(containsString("10.7.7.7"))));
    }
}
