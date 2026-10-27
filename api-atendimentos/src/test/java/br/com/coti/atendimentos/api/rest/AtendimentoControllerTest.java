package br.com.coti.atendimentos.api.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.atendimentos.aplicacao.ResponderPerguntaService;
import br.com.coti.atendimentos.dominio.Categoria;
import br.com.coti.atendimentos.dominio.Fonte;
import br.com.coti.atendimentos.dominio.PerguntaAluno;
import br.com.coti.atendimentos.dominio.ResultadoAtendimento;
import br.com.coti.atendimentos.dominio.excecao.BaseConhecimentoIndisponivelException;
import br.com.coti.atendimentos.dominio.excecao.ModeloIndisponivelException;
import br.com.coti.atendimentos.dominio.excecao.TempoEsgotadoException;
import br.com.coti.atendimentos.suporte.TesteWeb;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** POST /api/atendimentos/perguntar (RF06, RF09, RF10) com o caso de uso substituído por dublê. */
@TesteWeb
class AtendimentoControllerTest {

    private static final String ROTA = "/api/atendimentos/perguntar";
    private static final UUID FONTE_ID = UUID.fromString("c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f");
    private static final UUID DOCUMENTO_ID = UUID.fromString("3f6c2a1e-8b4d-4c7a-9e1f-2b3c4d5e6f70");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ResponderPerguntaService servico;

    private ResultActions perguntar(String corpo) throws Exception {
        return mvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private static String corpo(String pergunta) {
        return "{\"pergunta\":\"" + pergunta + "\"}";
    }

    private static ResultadoAtendimento comEvidencia() {
        return new ResultadoAtendimento("Sim. Recomenda-se lógica de programação.", true, List.of(
                new Fonte(FONTE_ID, DOCUMENTO_ID, "Curso Java Fullstack — pré-requisitos",
                        "Site COTI — página do curso Java Fullstack, out/2026", Categoria.PRE_REQUISITO, 0.86)),
                OffsetDateTime.parse("2026-10-27T14:32:05-03:00"));
    }

    // ---- 400: nunca chama o caso de uso

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "ab"})
    void perguntaCurtaDevolve400SemChamarOCasoDeUso(String pergunta) throws Exception {
        perguntar(corpo(pergunta)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erros[0].campo").value("pergunta"));

        verifyNoInteractions(servico);
    }

    @Test
    void perguntaComMilEUmCaracteresDevolve400SemChamarOCasoDeUsoNemEcoar() throws Exception {
        perguntar(corpo("segredo" + "x".repeat(994))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("pergunta"))
                .andExpect(content().string(not(containsString("segredo"))));

        verifyNoInteractions(servico);
    }

    @Test
    void perguntaComMilCaracteresEAceita() throws Exception {
        when(servico.responder(any())).thenReturn(comEvidencia());

        perguntar(corpo("x".repeat(1_000))).andExpect(status().isOk());
    }

    @Test
    void campoExtraDevolve400SemChamarOCasoDeUso() throws Exception {
        perguntar("{\"pergunta\":\"pergunta válida\",\"limite\":10}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("limite"));

        verifyNoInteractions(servico);
    }

    @Test
    void semAPerguntaDevolve400() throws Exception {
        perguntar("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros[0].campo").value("pergunta"));
        perguntar("{\"pergunta\":null}").andExpect(status().isBadRequest());

        verifyNoInteractions(servico);
    }

    @Test
    void perguntaQueFicaCurtaDepoisDeRemoverEspacosDevolve400() throws Exception {
        perguntar(corpo("  ab ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erros[0].campo").value("pergunta"));

        verifyNoInteractions(servico);
    }

    @Test
    void tiposDeMidiaEMetodosErrados() throws Exception {
        mvc.perform(post(ROTA).contentType(MediaType.TEXT_PLAIN).content("pergunta"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get(ROTA)).andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(servico);
    }

    // ---- 200 no formato do contrato

    @Test
    void comEvidenciaDevolve200NoFormatoDoContrato() throws Exception {
        when(servico.responder(any())).thenReturn(comEvidencia());

        perguntar(corpo("Preciso saber lógica de programação para fazer o curso de Java?")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resposta").value("Sim. Recomenda-se lógica de programação."))
                .andExpect(jsonPath("$.conhecimentoSuficiente").value(true))
                .andExpect(jsonPath("$.fontes", hasSize(1)))
                .andExpect(jsonPath("$.fontes[0].id").value(FONTE_ID.toString()))
                .andExpect(jsonPath("$.fontes[0].documentoId").value(DOCUMENTO_ID.toString()))
                .andExpect(jsonPath("$.fontes[0].titulo").value("Curso Java Fullstack — pré-requisitos"))
                .andExpect(jsonPath("$.fontes[0].fonte").value("Site COTI — página do curso Java Fullstack, out/2026"))
                .andExpect(jsonPath("$.fontes[0].categoria").value("PRE_REQUISITO"))
                .andExpect(jsonPath("$.fontes[0].similaridade").value(0.86))
                .andExpect(jsonPath("$.geradoEm").value("2026-10-27T14:32:05-03:00"));
    }

    @Test
    void oAtendimentoIdEIgualAoCabecalhoDeCorrelacao() throws Exception {
        when(servico.responder(any())).thenReturn(comEvidencia());

        var resposta = perguntar(corpo("pergunta válida")).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(resposta.getHeader("X-Correlation-Id")).isNotBlank();
        assertThat(resposta.getContentAsString()).contains("\"atendimentoId\":\"" + resposta.getHeader("X-Correlation-Id") + "\"");
    }

    @Test
    void semEvidenciaDevolve200ComFontesVazias() throws Exception {
        when(servico.responder(any())).thenReturn(ResultadoAtendimento.semEvidencia(
                "Não encontrei essa informação. Fale conosco.", OffsetDateTime.parse("2026-10-27T14:33:10-03:00")));

        perguntar(corpo("a COTI oferece bolsa de 100%?")).andExpect(status().isOk())
                .andExpect(jsonPath("$.conhecimentoSuficiente").value(false))
                .andExpect(jsonPath("$.fontes", hasSize(0)))
                .andExpect(jsonPath("$.resposta").value("Não encontrei essa informação. Fale conosco."));
    }

    @Test
    void similaridadeNulaSaiComoNullPoisOCampoEObrigatorioNoContrato() throws Exception {
        when(servico.responder(any())).thenReturn(new ResultadoAtendimento("Sim.", true, List.of(
                new Fonte(FONTE_ID, DOCUMENTO_ID, "T", "F", Categoria.CURSO, null)), OffsetDateTime.now()));

        perguntar(corpo("pergunta válida")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fontes[0].similaridade").value((Object) null))
                .andExpect(content().string(containsString("\"similaridade\":null")));
    }

    @Test
    void aRespostaNaoTrazNadaAlemDoContrato() throws Exception {
        when(servico.responder(any())).thenReturn(comEvidencia());

        perguntar(corpo("pergunta válida")).andExpect(status().isOk())
                .andExpect(jsonPath("$.prompt").doesNotExist())
                .andExpect(jsonPath("$.modelo").doesNotExist())
                .andExpect(jsonPath("$.tokens").doesNotExist())
                .andExpect(jsonPath("$.fontes[0].texto").doesNotExist())
                .andExpect(content().string(not(containsString(UUID.randomUUID().toString()))));
    }

    @Test
    void oCasoDeUsoRecebeAPerguntaSemEspacosNasPontas() throws Exception {
        when(servico.responder(any())).thenReturn(comEvidencia());

        perguntar(corpo("  pergunta válida  ")).andExpect(status().isOk());

        ArgumentCaptor<PerguntaAluno> recebida = ArgumentCaptor.forClass(PerguntaAluno.class);
        verify(servico).responder(recebida.capture());
        assertThat(recebida.getValue().texto()).isEqualTo("pergunta válida");
    }

    @Test
    void naoPrecisaDeCredencial() throws Exception {
        when(servico.responder(any())).thenReturn(comEvidencia());

        perguntar(corpo("pergunta válida")).andExpect(status().isOk());
    }

    // ---- falhas de dependência: 503 do contrato, com Retry-After e sem detalhe

    private void assert503(String codigo) throws Exception {
        var resposta = perguntar(corpo("pergunta válida")).andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("detalhe-interno"))))
                .andReturn().getResponse();

        assertThat(resposta.getContentAsString())
                .contains("\"correlacaoId\":\"" + resposta.getHeader("X-Correlation-Id") + "\"");
    }

    @Test
    void baseIndisponivelDevolve503() throws Exception {
        when(servico.responder(any())).thenThrow(new BaseConhecimentoIndisponivelException("detalhe-interno: http://x"));

        assert503("BASE_CONHECIMENTO_INDISPONIVEL");
    }

    @Test
    void modeloIndisponivelDevolve503() throws Exception {
        when(servico.responder(any())).thenThrow(new ModeloIndisponivelException("detalhe-interno"));

        assert503("MODELO_INDISPONIVEL");
    }

    @Test
    void tempoEsgotadoDevolve503() throws Exception {
        when(servico.responder(any())).thenThrow(new TempoEsgotadoException());

        assert503("TEMPO_ESGOTADO");
    }

    @Test
    void erroNaoPrevistoDevolve500GenericoSemDetalhe() throws Exception {
        when(servico.responder(any())).thenThrow(new IllegalStateException("detalhe-interno"));

        perguntar(corpo("pergunta válida")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"))
                .andExpect(content().string(not(containsString("detalhe-interno"))));
    }
}
