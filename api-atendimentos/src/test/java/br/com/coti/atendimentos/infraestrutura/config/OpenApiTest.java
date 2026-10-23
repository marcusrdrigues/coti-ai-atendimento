package br.com.coti.atendimentos.infraestrutura.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.atendimentos.suporte.ChavesTeste;
import br.com.coti.atendimentos.suporte.TesteWeb;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

class OpenApiTest {

    @TesteWeb
    @Nested
    class PerfilPadrao {

        @Autowired
        private MockMvc mvc;

        @Test
        void especificacaoExigeAChaveDeMonitoramento() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
            mvc.perform(get("/v3/api-docs").header("X-Api-Key", ChavesTeste.MONITORAMENTO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
                    .andExpect(jsonPath("$.info.title").value("COTI AI Atendimento — api-atendimentos"));
        }

        @Test
        void swaggerUiNaoEstaDisponivel() throws Exception {
            mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
            mvc.perform(get("/swagger-ui/index.html").header("X-Api-Key", ChavesTeste.MONITORAMENTO))
                    .andExpect(status().isNotFound());
        }
    }

    @TesteWeb
    @ActiveProfiles("local")
    @Nested
    class PerfilLocal {

        @Autowired
        private MockMvc mvc;

        @Test
        void especificacaoEPublica() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        }

        @Test
        void swaggerUiEstaDisponivel() throws Exception {
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }
}
