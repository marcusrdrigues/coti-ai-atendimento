package br.com.coti.atendimentos.api.erro;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Substitui o {@code /error} padrão do Spring Boot: erros gerados pelo contêiner (por exemplo, requisição
 * malformada) também saem no formato Problem Details do contrato.
 */
@Controller
@RequestMapping("/error")
public class ErroContainerController implements ErrorController {

    @RequestMapping
    public ResponseEntity<ProblemDetail> tratar(HttpServletRequest request) {
        Object atributo = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = atributo instanceof Integer s ? s : 500;
        CodigoErro codigo = CodigoErro.paraStatus(status);
        ProblemDetail problema = ProblemaFactory.criar(codigo, request);
        if (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String uri) {
            try {
                problema.setInstance(URI.create(uri));
            } catch (IllegalArgumentException ignorada) {
                // mantém a instância padrão
            }
        }
        return ResponseEntity.status(codigo.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
    }
}
