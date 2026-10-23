package br.com.coti.conhecimento.api.erro;

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
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import br.com.coti.conhecimento.suporte.TesteWeb;

@TesteWeb
@Import(ManipuladorErrosRestTest.ControladorDeTeste.class)
class ManipuladorErrosRestTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext contexto;

    /** Controlador só de teste: importado só por esta classe de teste (classes internas de teste não entram na varredura). */
    @Controller
    @RequestMapping("/api/teste")
    @ResponseBody
    @Validated
    static class ControladorDeTeste {

        record Entrada(@NotBlank @Size(max = 5) String nome) {
        }

        @PostMapping("/validar")
        Map<String, String> validar(@Valid @RequestBody Entrada entrada) {
            return Map.of("nome", entrada.nome());
        }

        @GetMapping("/explodir")
        String explodir() {
            throw new IllegalStateException("detalhe-interno-que-nao-pode-vazar");
        }

        @GetMapping("/uuid/{id}")
        String uuid(@PathVariable UUID id) {
            return id.toString();
        }

        @GetMapping("/pagina")
        String pagina(@RequestParam @Min(1) @Max(100) int tamanho) {
            return "ok";
        }
    }

    private ResultActions postarJson(String corpo) throws Exception {
        return mvc.perform(post("/api/teste/validar")
                .header("X-Api-Key", ChavesTeste.ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private ResultActions comoGestor(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
            throws Exception {
        return mvc.perform(req.header("X-Api-Key", ChavesTeste.ADMIN));
    }

    @Test
    void deveDevolverProblemDetailsComExtensoesDoContratoEmErroDeValidacao() throws Exception {
        postarJson("{\"nome\":\"nome-muito-longo-secreto\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.type").value("urn:coti-ai:problema:dados-invalidos"))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("A requisição contém campos inválidos."))
                .andExpect(jsonPath("$.instance").value("/api/teste/validar"))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.correlacaoId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.erros[0].campo").value("nome"))
                .andExpect(jsonPath("$.erros[0].mensagem").isNotEmpty())
                // o valor enviado nunca é devolvido
                .andExpect(content().string(not(containsString("secreto"))));
    }

    @Test
    void deveUsarOMesmoIdNoCabecalhoENoCorpo() throws Exception {
        String cabecalho = postarJson("{\"nome\":\"\"}").andReturn().getResponse().getHeader("X-Correlation-Id");
        String corpo = postarJson("{\"nome\":\"\"}").andReturn().getResponse().getContentAsString();

        assertThat(cabecalho).isNotBlank();
        assertThat(corpo).contains("\"correlacaoId\":\"");
        // dois pedidos distintos têm ids distintos; cada resposta é coerente consigo mesma
        var r = postarJson("{\"nome\":\"\"}").andReturn().getResponse();
        assertThat(r.getContentAsString()).contains(r.getHeader("X-Correlation-Id"));
    }

    @Test
    void deveRejeitarCampoDesconhecidoSemEcoarOValor() throws Exception {
        postarJson("{\"nome\":\"ok\",\"extra\":\"valor-secreto\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erros[0].campo").value("extra"))
                .andExpect(jsonPath("$.erros[0].mensagem").value("campo não reconhecido"))
                .andExpect(content().string(not(containsString("valor-secreto"))));
    }

    @Test
    void deveRejeitarJsonMalformado() throws Exception {
        postarJson("{nome:")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail").value("A requisição contém campos inválidos."));
    }

    @Test
    void deveDevolver405ComCodigoDoCatalogo() throws Exception {
        comoGestor(get("/api/teste/validar"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NAO_PERMITIDO"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void deveDevolver415ParaTipoDeMidiaNaoSuportado() throws Exception {
        mvc.perform(post("/api/teste/validar").header("X-Api-Key", ChavesTeste.ADMIN)
                        .contentType(MediaType.TEXT_PLAIN).content("texto"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_MIDIA_NAO_SUPORTADO"))
                .andExpect(jsonPath("$.detail").value("Use application/json."));
    }

    @Test
    void deveDevolver404ParaRecursoInexistente() throws Exception {
        comoGestor(get("/api/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.instance").value("/api/nao-existe"));
    }

    @Test
    void deveDevolver400ParaIdQueNaoEUuid() throws Exception {
        comoGestor(get("/api/teste/uuid/nao-e-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erros[0].campo").value("id"))
                // o caminho aparece em "instance" (contrato); o valor não pode aparecer na mensagem
                .andExpect(jsonPath("$.erros[0].mensagem").value(not(containsString("nao-e-uuid"))))
                .andExpect(jsonPath("$.detail").value(not(containsString("nao-e-uuid"))));
    }

    @Test
    void deveValidarParametroDeConsulta() throws Exception {
        comoGestor(get("/api/teste/pagina").param("tamanho", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erros[0].campo").value("tamanho"));
        comoGestor(get("/api/teste/pagina").param("tamanho", "100")).andExpect(status().isOk());
    }

    @Test
    void deveExigirParametroObrigatorio() throws Exception {
        comoGestor(get("/api/teste/pagina"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("tamanho"))
                .andExpect(jsonPath("$.erros[0].mensagem").value("parâmetro obrigatório"));
    }

    @Test
    void deveDevolver500GenericoSemDetalheInterno() throws Exception {
        comoGestor(get("/api/teste/explodir"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"))
                .andExpect(jsonPath("$.detail").value(CodigoErro.ERRO_INTERNO.detalhe()))
                .andExpect(content().string(not(containsString("detalhe-interno"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at br.com"))));
    }

    @Test
    void deveDevolver413QuandoOContentLengthPassaDoLimite() throws Exception {
        byte[] corpo = new byte[262_145];
        mvc.perform(post("/api/teste/validar").header("X-Api-Key", ChavesTeste.ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.codigo").value("CONTEUDO_GRANDE_DEMAIS"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void deveFormatarErrosDoContainerNoMesmoPadrao() throws Exception {
        mvc.perform(get("/error").with(requisicao -> {
                    requisicao.setDispatcherType(DispatcherType.ERROR);
                    requisicao.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
                    requisicao.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/qualquer");
                    return requisicao;
                }))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.instance").value("/api/qualquer"));
    }

    @Test
    void naoDeveExistirOControladorDeErroPadraoDoBoot() {
        assertThat(contexto.getBeansOfType(BasicErrorController.class)).isEmpty();
        assertThat(contexto.getBeansOfType(ErroContainerController.class)).hasSize(1);
    }
}
