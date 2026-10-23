package br.com.coti.atendimentos.infraestrutura.observabilidade;

import br.com.coti.atendimentos.api.erro.CorrelacaoHttp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Garante o {@code X-Correlation-Id} em toda resposta e o disponibiliza no MDC dos logs. Se a requisição traz
 * um {@code traceparent} W3C válido, o identificador deriva do trace id; caso contrário é gerado aqui. O
 * {@code X-Correlation-Id} enviado pelo cliente é ignorado (contrato, seção 1.3).
 *
 * <p>Roda antes de toda a cadeia de segurança, para que até as respostas 401 tenham o identificador.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelacaoFilter extends OncePerRequestFilter {

    private static final Pattern TRACEPARENT =
            Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");
    private static final String TRACE_ZERADO = "0".repeat(32);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = traceIdDaRequisicao(request);
        String correlacaoId = comoUuid(traceId);

        request.setAttribute(CorrelacaoHttp.ATRIBUTO, correlacaoId);
        response.setHeader(CorrelacaoHttp.CABECALHO, correlacaoId);
        MDC.put(CorrelacaoHttp.MDC_CORRELACAO, correlacaoId);
        MDC.put(CorrelacaoHttp.MDC_TRACE, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelacaoHttp.MDC_CORRELACAO);
            MDC.remove(CorrelacaoHttp.MDC_TRACE);
        }
    }

    private static String traceIdDaRequisicao(HttpServletRequest request) {
        String cabecalho = request.getHeader("traceparent");
        if (cabecalho != null) {
            Matcher m = TRACEPARENT.matcher(cabecalho.trim());
            if (m.matches() && !TRACE_ZERADO.equals(m.group(1))) {
                return m.group(1);
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Formata 32 caracteres hexadecimais como UUID (8-4-4-4-12). */
    static String comoUuid(String hex32) {
        return hex32.substring(0, 8) + "-" + hex32.substring(8, 12) + "-" + hex32.substring(12, 16) + "-"
                + hex32.substring(16, 20) + "-" + hex32.substring(20);
    }
}
