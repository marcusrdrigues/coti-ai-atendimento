package br.com.coti.atendimentos.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.atendimentos.suporte.AmbienteIntegrado;
import br.com.coti.atendimentos.suporte.ChavesTeste;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * T053: a {@code api-atendimentos} contra a {@code api-conhecimento} de verdade (o JAR empacotado, com PostgreSQL
 * e pgvector reais). O único dublê é a OpenAI, que só responde embeddings. Prova o fluxo ponta a ponta até o
 * ponto em que a Fase 6 termina: pergunta mascarada → ferramenta MCP → busca vetorial → sem evidência, ou
 * trechos até o gerador (que só existe na Fase 7).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "OPENAI_API_KEY=" + ChavesTeste.OPENAI,
        "OPENAI_CHAT_MODEL=" + ChavesTeste.MODELO_CHAT,
        "MONITORAMENTO_API_KEY=" + ChavesTeste.MONITORAMENTO,
        "ATENDIMENTO_MENSAGEM_SEM_EVIDENCIA=" + ChavesTeste.SEM_EVIDENCIA,
        "ATENDIMENTO_CANAL_HUMANO=" + ChavesTeste.CANAL_HUMANO
})
@AutoConfigureMockMvc
class ServicosIntegradosIT {

    private static AmbienteIntegrado ambiente;

    @BeforeAll
    static void subirAmbiente() {
        ambiente = AmbienteIntegrado.obter();
    }

    @DynamicPropertySource
    static void conectarAoConhecimento(DynamicPropertyRegistry registro) {
        registro.add("CONHECIMENTO_MCP_URL", () -> ambiente.urlConhecimento());
        registro.add("CONHECIMENTO_MCP_API_KEY", () -> ambiente.chaveMcp());
    }

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void limpar() {
        ambiente.openAi().limpar();
    }

    private ResultActions perguntar(String pergunta) throws Exception {
        return mvc.perform(post("/api/atendimentos/perguntar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"pergunta\":\"" + pergunta + "\"}"));
    }

    @Test
    void perguntaSemEvidenciaDevolve200SemFontesComAMensagemConfigurada() throws Exception {
        perguntar("Qual a previsão do tempo para amanhã em São Paulo?").andExpect(status().isOk())
                .andExpect(jsonPath("$.conhecimentoSuficiente").value(false))
                .andExpect(jsonPath("$.fontes").isEmpty())
                .andExpect(jsonPath("$.resposta").value(ChavesTeste.SEM_EVIDENCIA + " " + ChavesTeste.CANAL_HUMANO));
    }

    @Test
    void oDadoPessoalNuncaChegaAOpenAiPoisAPerguntaSaiMascarada() throws Exception {
        perguntar("Meu CPF é 529.982.247-25 e meu email é maria@exemplo.com.br. Qual o valor do curso?")
                .andExpect(status().isOk());

        assertThat(ambiente.openAi().textosRecebidos()).hasSize(1);
        String enviado = ambiente.openAi().textosRecebidos().get(0);
        assertThat(enviado).contains("[CPF]", "[EMAIL]", "Qual o valor do curso?")
                .doesNotContain("529.982.247-25", "maria@exemplo.com.br");
    }

    @Test
    void perguntaComEvidenciaChegaAoGeradorQueAindaNaoExisteNaFase6() throws Exception {
        String conteudo = "Para o curso Java Fullstack é recomendado conhecimento básico de lógica de programação "
                + "e familiaridade com o computador. Identificador " + UUID.randomUUID() + ".";
        assertThat(ambiente.cadastrarConhecimento("Curso Java — pré-requisitos", conteudo, "Site COTI, out/2026",
                "PRE_REQUISITO")).isEqualTo(201);
        assertThat(ambiente.openAi().textosRecebidos()).as("na ingestão o embedding recebe só o texto do trecho")
                .containsExactly(conteudo);
        ambiente.openAi().limpar();

        // O trecho foi recuperado pela ferramenta MCP (senão a resposta seria "sem evidência" com 200). Como o
        // gerador de respostas só entra na Fase 7, o fluxo termina em 503 MODELO_INDISPONIVEL. Na Fase 7 este
        // teste passa a esperar 200 com a fonte do documento cadastrado.
        perguntar(conteudo).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("MODELO_INDISPONIVEL"));
        assertThat(ambiente.openAi().textosRecebidos()).containsExactly(conteudo);
    }
}
