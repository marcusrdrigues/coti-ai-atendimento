package br.com.coti.conhecimento.infraestrutura.observabilidade;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.coti.conhecimento.api.erro.CorrelacaoHttp;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelacaoFilterTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    private final CorrelacaoFilter filtro = new CorrelacaoFilter();

    @Test
    void deveDerivarOIdDoTraceparentValido() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader("traceparent", TRACEPARENT);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcNaCadeia = new AtomicReference<>();
        AtomicReference<String> traceNaCadeia = new AtomicReference<>();
        FilterChain cadeia = (rq, rs) -> {
            mdcNaCadeia.set(MDC.get(CorrelacaoHttp.MDC_CORRELACAO));
            traceNaCadeia.set(MDC.get(CorrelacaoHttp.MDC_TRACE));
        };

        filtro.doFilter(request, response, cadeia);

        String esperado = "4bf92f35-77b3-4da6-a3ce-929d0e0e4736";
        assertThat(response.getHeader(CorrelacaoHttp.CABECALHO)).isEqualTo(esperado);
        assertThat(request.getAttribute(CorrelacaoHttp.ATRIBUTO)).isEqualTo(esperado);
        assertThat(mdcNaCadeia.get()).isEqualTo(esperado);
        assertThat(traceNaCadeia.get()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void deveGerarUmUuidQuandoNaoHaTraceparent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filtro.doFilter(request, response, (rq, rs) -> { });

        String id = response.getHeader(CorrelacaoHttp.CABECALHO);
        assertThat(id).isNotNull();
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void deveIgnorarTraceparentInvalidoOuZerado() throws Exception {
        for (String invalido : new String[] {
                "lixo", "00-" + "0".repeat(32) + "-00f067aa0ba902b7-01", "00-4bf92f3577b34da6a3ce929d0e0e47-00f067aa0ba902b7-01"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
            request.addHeader("traceparent", invalido);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filtro.doFilter(request, response, (rq, rs) -> { });

            assertThat(UUID.fromString(response.getHeader(CorrelacaoHttp.CABECALHO))).isNotNull();
            assertThat(response.getHeader(CorrelacaoHttp.CABECALHO)).doesNotStartWith("4bf92f35");
        }
    }

    @Test
    void deveIgnorarOIdEnviadoPeloCliente() throws Exception {
        String doCliente = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader(CorrelacaoHttp.CABECALHO, doCliente);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filtro.doFilter(request, response, (rq, rs) -> { });

        assertThat(response.getHeader(CorrelacaoHttp.CABECALHO)).isNotEqualTo(doCliente);
    }

    @Test
    void deveLimparOMdcAoFinal() throws Exception {
        filtro.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), (rq, rs) -> { });

        assertThat(MDC.get(CorrelacaoHttp.MDC_CORRELACAO)).isNull();
        assertThat(MDC.get(CorrelacaoHttp.MDC_TRACE)).isNull();
    }

    @Test
    void deveLimparOMdcMesmoQuandoACadeiaFalha() {
        FilterChain falha = (rq, rs) -> {
            throw new IllegalStateException("falha simulada");
        };

        try {
            filtro.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), falha);
        } catch (Exception esperado) {
            // a exceção é propagada; o que importa é a limpeza do MDC
        }

        assertThat(MDC.get(CorrelacaoHttp.MDC_CORRELACAO)).isNull();
    }
}
