package br.com.coti.conhecimento.api.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.conhecimento.suporte.ChavesTeste;
import br.com.coti.conhecimento.suporte.TesteWeb;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Comportamento real da Fase 3, com o adaptador provisório de indexação (que sempre falha): um cadastro sem
 * pendências não pode parecer publicado sem trechos. Este teste muda na Fase 4, quando a indexação existir.
 */
@TesteWeb
class CadastroSemIndexadorIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void limpar() {
        jdbc.sql("DELETE FROM documento").update();
    }

    @Test
    void cadastroSemPendenciaTerminaEmErroDeProcessamentoSemTrechos() throws Exception {
        String corpo = "{\"titulo\":\"Curso\",\"conteudo\":\"O curso de Angular tem modalidades presencial e online. "
                + UUID.randomUUID() + "\",\"fonte\":\"Fonte\",\"categoria\":\"MODALIDADE\"}";

        String resposta = mvc.perform(post("/api/conhecimentos").header("X-Api-Key", ChavesTeste.ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("MODELO_INDISPONIVEL"))
                .andExpect(header().exists("Retry-After"))
                .andReturn().getResponse().getContentAsString();

        String id = JsonMapper.builder().build().readTree(resposta).get("conhecimentoId").asString();
        mvc.perform(get("/api/conhecimentos/" + id).header("X-Api-Key", ChavesTeste.ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ERRO_PROCESSAMENTO"));
        assertThat(jdbc.sql("SELECT count(*) FROM documento WHERE status = 'PUBLICADO'").query(Long.class).single())
                .isZero();
    }
}
