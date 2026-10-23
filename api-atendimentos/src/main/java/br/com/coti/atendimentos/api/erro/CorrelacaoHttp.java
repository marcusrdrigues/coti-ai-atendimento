package br.com.coti.atendimentos.api.erro;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.MDC;

/** Nomes e acesso ao identificador de correlação de cada requisição (specs/06-api-contracts.md §1.3). */
public final class CorrelacaoHttp {

    public static final String CABECALHO = "X-Correlation-Id";
    public static final String ATRIBUTO = CorrelacaoHttp.class.getName() + ".ID";
    public static final String MDC_CORRELACAO = "correlacaoId";
    public static final String MDC_TRACE = "traceId";

    private CorrelacaoHttp() {
    }

    /** Identificador da requisição; gera um novo apenas se o filtro de correlação não tiver rodado. */
    public static String obter(HttpServletRequest request) {
        if (request != null && request.getAttribute(ATRIBUTO) instanceof String id) {
            return id;
        }
        String doMdc = MDC.get(MDC_CORRELACAO);
        return doMdc != null ? doMdc : UUID.randomUUID().toString();
    }
}
