package br.com.coti.atendimentos.api.erro;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.ProblemDetail;

/** Monta o corpo de erro padronizado: Problem Details (RFC 9457) com as extensões do contrato. */
public final class ProblemaFactory {

    private ProblemaFactory() {
    }

    public static ProblemDetail criar(CodigoErro codigo, HttpServletRequest request) {
        return criar(codigo, request, List.of());
    }

    public static ProblemDetail criar(CodigoErro codigo, HttpServletRequest request, List<ErroCampo> erros) {
        ProblemDetail problema = ProblemDetail.forStatus(codigo.status());
        completar(problema, codigo, request, erros);
        return problema;
    }

    /**
     * Sobrescreve tipo, título, detalhe e extensões de um {@link ProblemDetail} (inclusive os gerados pelo
     * Spring, cujo detalhe padrão poderia ecoar valores do cliente).
     */
    public static void completar(ProblemDetail problema, CodigoErro codigo, HttpServletRequest request,
                                 List<ErroCampo> erros) {
        problema.setType(URI.create(codigo.tipo()));
        problema.setTitle(codigo.titulo());
        problema.setDetail(codigo.detalhe());
        problema.setInstance(instancia(request));
        problema.setProperty("codigo", codigo.name());
        problema.setProperty("correlacaoId", CorrelacaoHttp.obter(request));
        problema.setProperty("timestamp", OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        if (erros != null && !erros.isEmpty()) {
            problema.setProperty("erros", erros);
        }
    }

    private static URI instancia(HttpServletRequest request) {
        try {
            return URI.create(request.getRequestURI());
        } catch (IllegalArgumentException e) {
            return URI.create("/");
        }
    }
}
