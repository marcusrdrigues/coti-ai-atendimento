package br.com.coti.conhecimento.infraestrutura.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.conhecimento.suporte.ChavesTeste;
import br.com.coti.conhecimento.suporte.EmbeddingModelFalso;
import br.com.coti.conhecimento.suporte.TesteWeb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Contrato §3.2 e §3.5: cadastro e diagnóstico têm baldes separados por credencial (aqui 2 e 3 por minuto). */
@TesteWeb
@TestPropertySource(properties = {
        "coti.conhecimento.limites-requisicao.cadastro-por-minuto=2",
        "coti.conhecimento.limites-requisicao.diagnostico-por-minuto=3"})
class LimiteRequisicoesIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EmbeddingModelFalso embeddings;

    @Autowired
    private BaldesDeRequisicao baldes;

    @BeforeEach
    void limpar() {
        embeddings.reiniciar();
        baldes.limpar();
    }

    private ResultActions cadastrar(String chave) throws Exception {
        // Corpo inválido de propósito (400): conta no limite e não chega ao provedor de embeddings.
        return mvc.perform(post("/api/conhecimentos").header("X-Api-Key", chave)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private ResultActions diagnosticar(String chave) throws Exception {
        return mvc.perform(post("/api/conhecimentos/diagnostico/busca").header("X-Api-Key", chave)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pergunta\":\"ab\"}"));
    }

    @Test
    void oCadastroTemUmBaldeDeDoisPorMinutoEAterceiraRecebe429ComRetryAfter() throws Exception {
        cadastrar(ChavesTeste.ADMIN).andExpect(status().isBadRequest());
        cadastrar(ChavesTeste.ADMIN).andExpect(status().isBadRequest());

        var resposta = cadastrar(ChavesTeste.ADMIN).andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("LIMITE_REQUISICOES"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn().getResponse();

        assertThat(Long.parseLong(resposta.getHeader("Retry-After"))).isBetween(1L, 60L);
        assertThat(resposta.getContentAsString())
                .contains("\"correlacaoId\":\"" + resposta.getHeader("X-Correlation-Id") + "\"");
    }

    @Test
    void oDiagnosticoTemOSeuProprioBaldeDeTresPorMinuto() throws Exception {
        for (int i = 0; i < 3; i++) {
            diagnosticar(ChavesTeste.ADMIN).andExpect(status().isBadRequest());
        }

        diagnosticar(ChavesTeste.ADMIN).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("LIMITE_REQUISICOES"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void esgotarUmBaldeNaoAfetaOOutro() throws Exception {
        for (int i = 0; i < 3; i++) {
            cadastrar(ChavesTeste.ADMIN);
        }
        cadastrar(ChavesTeste.ADMIN).andExpect(status().isTooManyRequests());

        diagnosticar(ChavesTeste.ADMIN).andExpect(status().isBadRequest());
    }

    @Test
    void semCredencialValidaRecebe401EONaoGastaFicha() throws Exception {
        for (int i = 0; i < 5; i++) {
            cadastrar("chave-errada").andExpect(status().isUnauthorized());
            cadastrar(ChavesTeste.MONITORAMENTO).andExpect(status().isUnauthorized());
        }

        cadastrar(ChavesTeste.ADMIN).andExpect(status().isBadRequest());
    }

    @Test
    void asConsultasNaoTemLimite() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/conhecimentos").header("X-Api-Key", ChavesTeste.ADMIN)).andExpect(status().isOk());
        }
    }

    @Test
    void aRecusaNaoVazaDetalheNemAChave() throws Exception {
        for (int i = 0; i < 2; i++) {
            cadastrar(ChavesTeste.ADMIN);
        }

        cadastrar(ChavesTeste.ADMIN).andExpect(status().isTooManyRequests())
                .andExpect(content().string(not(containsString(ChavesTeste.ADMIN))))
                .andExpect(content().string(not(containsString("gestor"))));
    }
}
