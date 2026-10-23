package br.com.coti.atendimentos.api.erro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.atendimentos.suporte.ChavesTeste;
import br.com.coti.atendimentos.suporte.TesteWeb;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Testa o tratamento de erros pela única rota pública da API. O controlador de teste substitui, nesta classe,
 * o controlador real da Fase 6; ele deixa de existir quando o real for criado.
 */
@TesteWeb
@Import(ManipuladorErrosRestTest.ControladorDeTeste.class)
class ManipuladorErrosRestTest {

    private static final String ROTA = "/api/atendimentos/perguntar";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext contexto;

    /** Controlador só de teste: importado só por esta classe (classes internas de teste não entram na varredura). */
    @Controller
    @RequestMapping(ROTA)
    @ResponseBody
    static class ControladorDeTeste {

        record Entrada(@NotBlank @Size(max = 5) String pergunta) {
        }

        @PostMapping
        Map<String, String> perguntar(@Valid @RequestBody Entrada entrada) {
            if ("boom".equals(entrada.pergunta())) {
                throw new IllegalStateException("detalhe-interno-que-nao-pode-vazar");
            }
            return Map.of("pergunta", entrada.pergunta());
        }
    }

    private ResultActions postarJson(String corpo) throws Exception {
        return mvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    @Test
    void deveDevolverProblemDetailsComExtensoesDoContratoEmErroDeValidacao() throws Exception {
        postarJson("{\"pergunta\":\"pergunta-muito-longa-secreta\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.type").value("urn:coti-ai:problema:dados-invalidos"))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("A requisição contém campos inválidos."))
                .andExpect(jsonPath("$.instance").value(ROTA))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.correlacaoId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.erros[0].campo").value("pergunta"))
                .andExpect(content().string(not(containsString("secreta"))));
    }

    @Test
    void deveUsarOIdDoCabecalhoNoCorpoDoErro() throws Exception {
        var resposta = postarJson("{\"pergunta\":\"\"}").andReturn().getResponse();

        assertThat(resposta.getHeader("X-Correlation-Id")).isNotBlank();
        assertThat(resposta.getContentAsString()).contains(resposta.getHeader("X-Correlation-Id"));
    }

    @Test
    void deveRejeitarCampoDesconhecidoSemEcoarOValor() throws Exception {
        postarJson("{\"pergunta\":\"ok\",\"extra\":\"valor-secreto\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("extra"))
                .andExpect(jsonPath("$.erros[0].mensagem").value("campo não reconhecido"))
                .andExpect(content().string(not(containsString("valor-secreto"))));
    }

    @Test
    void deveRejeitarJsonMalformado() throws Exception {
        postarJson("{pergunta:")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void deveDevolver405ParaMetodoNaoPermitidoNaRotaPublica() throws Exception {
        mvc.perform(get(ROTA))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NAO_PERMITIDO"));
    }

    @Test
    void deveDevolver415ParaTipoDeMidiaNaoSuportado() throws Exception {
        mvc.perform(post(ROTA).contentType(MediaType.TEXT_PLAIN).content("texto"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_MIDIA_NAO_SUPORTADO"))
                .andExpect(jsonPath("$.detail").value("Use application/json."));
    }

    @Test
    void deveDevolver404ParaRecursoInexistente() throws Exception {
        mvc.perform(get("/actuator/nao-existe").header("X-Api-Key", ChavesTeste.MONITORAMENTO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void deveDevolver500GenericoSemDetalheInterno() throws Exception {
        postarJson("{\"pergunta\":\"boom\"}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"))
                .andExpect(jsonPath("$.detail").value(CodigoErro.ERRO_INTERNO.detalhe()))
                .andExpect(content().string(not(containsString("detalhe-interno"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at br.com"))));
    }

    @Test
    void deveDevolver413QuandoOContentLengthPassaDoLimite() throws Exception {
        mvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content(new byte[262_145]))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.codigo").value("CONTEUDO_GRANDE_DEMAIS"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void deveFormatarErrosDoContainerNoMesmoPadrao() throws Exception {
        mvc.perform(get("/error").with(requisicao -> {
                    requisicao.setDispatcherType(DispatcherType.ERROR);
                    requisicao.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
                    requisicao.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/qualquer");
                    return requisicao;
                }))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.instance").value("/qualquer"));
    }

    @Test
    void naoDeveExistirOControladorDeErroPadraoDoBoot() {
        assertThat(contexto.getBeansOfType(BasicErrorController.class)).isEmpty();
        assertThat(contexto.getBeansOfType(ErroContainerController.class)).hasSize(1);
    }
}
