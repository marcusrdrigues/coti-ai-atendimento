package br.com.coti.atendimentos.infraestrutura.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.atendimentos.api.erro.CorpoGrandeDemaisException;
import br.com.coti.atendimentos.api.erro.EscritorProblema;
import br.com.coti.atendimentos.infraestrutura.config.PropriedadesAtendimentos;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class LimiteTamanhoCorpoFilterTest {

    private static final long LIMITE = 100;

    private final LimiteTamanhoCorpoFilter filtro = new LimiteTamanhoCorpoFilter(
            new PropriedadesAtendimentos(
                    new PropriedadesAtendimentos.Seguranca("a", "m"),
                    new PropriedadesAtendimentos.OpenAi("c", "d"),
                    new PropriedadesAtendimentos.Conhecimento("http://localhost:1"),
                    new PropriedadesAtendimentos.Mensagens("sem evidencia", "canal"),
                    new PropriedadesAtendimentos.Web(LIMITE),
                    new PropriedadesAtendimentos.Busca(5, 0.70),
                    new PropriedadesAtendimentos.Resiliencia(java.time.Duration.ofSeconds(5), 2, java.time.Duration.ofSeconds(30),
                            java.time.Duration.ofSeconds(30), java.time.Duration.ofMillis(200), 2, java.time.Duration.ofSeconds(2),
                            java.time.Duration.ofMillis(100), java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(10)),
                    false),
            new EscritorProblema(JsonMapper.builder().build()));

    @Test
    void deveRecusarComContentLengthAcimaDoLimiteSemChamarACadeia() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/conhecimentos");
        request.setContent(new byte[(int) LIMITE + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chamou = new AtomicBoolean();

        filtro.doFilter(request, response, (rq, rs) -> chamou.set(true));

        assertThat(chamou).isFalse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"codigo\":\"CONTEUDO_GRANDE_DEMAIS\"");
    }

    @Test
    void deveAceitarCorpoNoLimite() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/conhecimentos");
        request.setContent(new byte[(int) LIMITE]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chamou = new AtomicBoolean();

        filtro.doFilter(request, response, (rq, rs) -> chamou.set(true));

        assertThat(chamou).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void deveFalharAoLerAlemDoLimiteQuandoNaoHaContentLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/conhecimentos") {
            @Override
            public long getContentLengthLong() {
                return -1; // simula Transfer-Encoding: chunked
            }
        };
        request.setContent(new byte[(int) LIMITE + 1]);

        assertThatThrownBy(() -> filtro.doFilter(request, new MockHttpServletResponse(),
                (rq, rs) -> rq.getInputStream().readAllBytes()))
                .isInstanceOf(CorpoGrandeDemaisException.class);
    }

    @Test
    void devePermitirLerCorpoSemContentLengthDentroDoLimite() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/conhecimentos") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(new byte[(int) LIMITE]);
        AtomicBoolean leu = new AtomicBoolean();

        filtro.doFilter(request, new MockHttpServletResponse(), (rq, rs) -> {
            leu.set(rq.getInputStream().readAllBytes().length == LIMITE);
        });

        assertThat(leu).isTrue();
    }
}
