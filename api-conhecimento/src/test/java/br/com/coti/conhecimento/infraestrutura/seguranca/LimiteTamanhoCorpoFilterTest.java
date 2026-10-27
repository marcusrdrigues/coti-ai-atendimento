package br.com.coti.conhecimento.infraestrutura.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.conhecimento.api.erro.CorpoGrandeDemaisException;
import br.com.coti.conhecimento.api.erro.EscritorProblema;
import br.com.coti.conhecimento.infraestrutura.config.PropriedadesConhecimento;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class LimiteTamanhoCorpoFilterTest {

    private static final long LIMITE = 100;

    private final LimiteTamanhoCorpoFilter filtro = new LimiteTamanhoCorpoFilter(
            new PropriedadesConhecimento(
                    new PropriedadesConhecimento.Seguranca("a", "b", "m"),
                    new PropriedadesConhecimento.OpenAi("c"),
                    new PropriedadesConhecimento.Web(LIMITE),
                    new PropriedadesConhecimento.Embedding("modelo-de-teste"),
                    new PropriedadesConhecimento.Analise(java.util.List.of("x"), java.util.List.of(), java.util.List.of()),
                    new PropriedadesConhecimento.Divisao(1500, 200, 50, 500),
                    new PropriedadesConhecimento.Embeddings(100, 2, java.time.Duration.ofMillis(1), 2, java.time.Duration.ofMillis(5), java.time.Duration.ZERO),
                new PropriedadesConhecimento.Busca(3, 1000, 5, 10, 0.70, 2000, 8000, java.time.Duration.ofSeconds(5)),
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
