package br.com.coti.conhecimento.infraestrutura.seguranca;

import br.com.coti.conhecimento.api.erro.CodigoErro;
import br.com.coti.conhecimento.api.erro.EscritorProblema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Respostas 401 e 403 da cadeia de segurança, no formato Problem Details do contrato. */
@Component
public final class RespostaSegurancaProblema implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final EscritorProblema escritor;

    public RespostaSegurancaProblema(EscritorProblema escritor) {
        this.escritor = escritor;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        escritor.escrever(request, response, CodigoErro.NAO_AUTENTICADO);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        escritor.escrever(request, response, CodigoErro.ACESSO_NEGADO);
    }
}
