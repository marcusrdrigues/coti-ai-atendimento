package br.com.coti.conhecimento.api.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.conhecimento.suporte.ChavesTeste;
import br.com.coti.conhecimento.suporte.EmbeddingModelFalso;
import br.com.coti.conhecimento.suporte.TesteWeb;
import br.com.coti.conhecimento.suporte.TrechosDeTeste;
import br.com.coti.conhecimento.suporte.TrechosDeTeste.Trecho;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** POST /api/conhecimentos/diagnostico/busca (RF12): autenticação, validação, resposta e erros do contrato. */
@TesteWeb
class DiagnosticoBuscaControllerTest {

    private static final String URL = "/api/conhecimentos/diagnostico/busca";
    private static final String PERGUNTA = "Preciso saber programar antes do curso de Java?";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EmbeddingModelFalso embeddings;

    @BeforeEach
    void limpar() {
        TrechosDeTeste.zerar();
        embeddings.reiniciar();
    }

    private ResultActions buscar(String corpo) throws Exception {
        return mvc.perform(post(URL).header("X-Api-Key", ChavesTeste.ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private static String corpo(String pergunta, String limite) {
        return "{\"pergunta\":\"" + pergunta + "\"" + (limite == null ? "" : ",\"limite\":" + limite) + "}";
    }

    // ---- autenticação

    @Test
    void semChaveDevolve401() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(corpo(PERGUNTA, null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void chaveErradaEChaveDeMonitoramentoDevolvem401() throws Exception {
        for (String chave : new String[] {"errada", ChavesTeste.MONITORAMENTO}) {
            mvc.perform(post(URL).header("X-Api-Key", chave).contentType(MediaType.APPLICATION_JSON)
                    .content(corpo(PERGUNTA, null))).andExpect(status().isUnauthorized());
        }
        org.assertj.core.api.Assertions.assertThat(embeddings.chamadas()).isZero();
    }

    // ---- validação

    @Test
    void perguntaAusenteDevolve400ComOCampo() throws Exception {
        buscar("{\"limite\":3}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erros[0].campo").value("pergunta"));
    }

    @Test
    void perguntaCurtaDemaisDevolve400SemEcoDoValor() throws Exception {
        buscar(corpo("ab", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("pergunta"))
                .andExpect(content().string(not(containsString("\"ab\""))));
    }

    @Test
    void perguntaLongaDemaisDevolve400() throws Exception {
        buscar(corpo("a".repeat(1_001), null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("pergunta"))
                .andExpect(content().string(not(containsString("aaaaaaaaaa"))));
    }

    @Test
    void perguntaSoDeEspacosDevolve400PelaRegraDoCasoDeUso() throws Exception {
        buscar(corpo("          ", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erros[0].campo").value("pergunta"))
                .andExpect(jsonPath("$.erros[0].mensagem").value("O campo 'pergunta' deve ter entre 3 e 1.000 caracteres."));
        org.assertj.core.api.Assertions.assertThat(embeddings.chamadas()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "11", "-1", "1000"})
    void limiteForaDeUmADezDevolve400(String limite) throws Exception {
        buscar(corpo(PERGUNTA, limite)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("limite"));
    }

    @Test
    void limiteQueNaoEhNumeroDevolve400() throws Exception {
        buscar(corpo(PERGUNTA, "\"cinco\"")).andExpect(status().isBadRequest());
    }

    @Test
    void campoDesconhecidoDevolve400() throws Exception {
        buscar("{\"pergunta\":\"" + PERGUNTA + "\",\"status\":\"EM_REVISAO\"}").andExpect(status().isBadRequest());
    }

    @Test
    void semCorpoDevolve400() throws Exception {
        mvc.perform(post(URL).header("X-Api-Key", ChavesTeste.ADMIN).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tipoDeMidiaQueNaoEhJsonDevolve415() throws Exception {
        mvc.perform(post(URL).header("X-Api-Key", ChavesTeste.ADMIN).contentType(MediaType.TEXT_PLAIN)
                .content(PERGUNTA)).andExpect(status().isUnsupportedMediaType());
    }

    // ---- resposta

    @Test
    void devolveOsTrechosComOsCamposDoContratoEOsExtrasDoDiagnostico() throws Exception {
        Trecho a = Trecho.publicado("Conhecimento básico de lógica de programação é recomendado.", 0.91)
                .comCategoria("PRE_REQUISITO");
        TrechosDeTeste.gravar(jdbc, PERGUNTA, a);
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("Menos relacionado.", 0.78));

        buscar(corpo(PERGUNTA, "3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.trechos", hasSize(2)))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.trechos[0].id").value(a.id().toString()))
                .andExpect(jsonPath("$.trechos[0].texto").value("Conhecimento básico de lógica de programação é recomendado."))
                .andExpect(jsonPath("$.trechos[0].documentoId").value(a.documentoId().toString()))
                .andExpect(jsonPath("$.trechos[0].titulo").value("Título"))
                .andExpect(jsonPath("$.trechos[0].fonte").value("Fonte"))
                .andExpect(jsonPath("$.trechos[0].categoria").value("PRE_REQUISITO"))
                .andExpect(jsonPath("$.trechos[0].similaridade").isNumber())
                .andExpect(jsonPath("$.limiarSimilaridade").value(0.70))
                .andExpect(jsonPath("$.modeloEmbedding").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.duracaoMs", greaterThanOrEqualTo(0)))
                .andExpect(header().string("Content-Type", containsString("application/json")));
    }

    @Test
    void asSimilaridadesVemEmOrdemDecrescente() throws Exception {
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("fraco", 0.75));
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("forte", 0.95));

        buscar(corpo(PERGUNTA, null)).andExpect(jsonPath("$.trechos[0].texto").value("forte"))
                .andExpect(jsonPath("$.trechos[1].texto").value("fraco"));
    }

    @Test
    void naoDevolveMetadadosInternosNemVetor() throws Exception {
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("conteúdo", 0.9));

        buscar(corpo(PERGUNTA, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.trechos[0].status").doesNotExist())
                .andExpect(jsonPath("$.trechos[0].modeloEmbedding").doesNotExist())
                .andExpect(jsonPath("$.trechos[0].vigenteAte").doesNotExist())
                .andExpect(jsonPath("$.trechos[0].ordem").doesNotExist())
                .andExpect(jsonPath("$.trechos[0].embedding").doesNotExist());
    }

    @Test
    void semTrechoRelevanteDevolve200ComListaVazia() throws Exception {
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("distante", 0.40));

        buscar(corpo(PERGUNTA, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.trechos", hasSize(0)))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.limiarSimilaridade").value(0.70));
    }

    @Test
    void naoMostraTrechosNaoPublicados() throws Exception {
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("rascunho", 0.99).comStatus("EM_REVISAO"));
        TrechosDeTeste.gravar(jdbc, PERGUNTA, Trecho.publicado("em-processo", 0.98).comStatus("PROCESSANDO"));

        buscar(corpo(PERGUNTA, null)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void aPerguntaNaoApareceNaResposta() throws Exception {
        buscar(corpo(PERGUNTA, null)).andExpect(content().string(not(containsString("programar antes"))));
    }

    // ---- falhas

    @Test
    void embeddingIndisponivelDevolve503ModeloIndisponivelComRetryAfter() throws Exception {
        embeddings.falharSempre(EmbeddingModelFalso::limiteDeRequisicoes);

        buscar(corpo(PERGUNTA, null)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("MODELO_INDISPONIVEL"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().string(not(containsString("RateLimit"))));
    }

    @Test
    void credencialRecusadaPelaOpenAiTambemEhModeloIndisponivelSemVazarDetalhe() throws Exception {
        embeddings.falharSempre(EmbeddingModelFalso::credencialRecusada);

        buscar(corpo(PERGUNTA, null)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("MODELO_INDISPONIVEL"))
                .andExpect(content().string(not(containsString(ChavesTeste.OPENAI))));
    }
}
