package br.com.coti.atendimentos.api.erro;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Traduz exceções em Problem Details no padrão do contrato. As mensagens vêm do catálogo
 * ({@link CodigoErro}); valores recebidos do cliente nunca são devolvidos.
 */
@RestControllerAdvice
public class ManipuladorErrosRest extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManipuladorErrosRest.class);

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problema) {
            CodigoErro codigo = CodigoErro.paraStatus(statusCode.value());
            ProblemaFactory.completar(problema, codigo, requisicao(request), errosExistentes(problema));
            headers = comTipoProblema(headers);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErroCampo> erros = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> erros.add(new ErroCampo(e.getField(), mensagemSegura(e.getDefaultMessage()))));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> erros.add(new ErroCampo(e.getObjectName(), mensagemSegura(e.getDefaultMessage()))));
        return responderComErros(ex, erros, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErroCampo> erros = new ArrayList<>();
        ex.getParameterValidationResults().forEach(resultado -> {
            String campo = resultado.getMethodParameter().getParameterName();
            resultado.getResolvableErrors()
                    .forEach(e -> erros.add(new ErroCampo(campo, mensagemSegura(e.getDefaultMessage()))));
        });
        return responderComErros(ex, erros, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (contemCausa(ex, CorpoGrandeDemaisException.class)) {
            return handleExceptionInternal(ex, ProblemDetail.forStatus(413), headers,
                    HttpStatusCode.valueOf(413), request);
        }
        List<ErroCampo> erros = new ArrayList<>();
        Throwable causa = ex.getCause();
        if (causa instanceof UnrecognizedPropertyException desconhecida) {
            erros.add(new ErroCampo(String.valueOf(desconhecida.getPropertyName()), "campo não reconhecido"));
        } else if (causa instanceof MismatchedInputException invalida) {
            String campo = invalida.getPath().stream()
                    .map(r -> r.getPropertyName() != null ? r.getPropertyName() : "[" + r.getIndex() + "]")
                    .reduce((a, b) -> a + "." + b).orElse("corpo");
            erros.add(new ErroCampo(campo, "valor com formato inválido"));
        }
        return responderComErros(ex, erros, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return responderComErros(ex, List.of(new ErroCampo(ex.getParameterName(), "parâmetro obrigatório")),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return responderComErros(ex, List.of(new ErroCampo(String.valueOf(ex.getPropertyName()),
                "valor com formato inválido")), headers, status, request);
    }

    /** Validação de parâmetros por AOP (classe com @Validated): mesmo formato da validação do corpo. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> restricaoViolada(ConstraintViolationException ex, HttpServletRequest request) {
        List<ErroCampo> erros = ex.getConstraintViolations().stream()
                .map(v -> new ErroCampo(ultimoNo(v.getPropertyPath().toString()), mensagemSegura(v.getMessage())))
                .toList();
        return ResponseEntity.status(CodigoErro.DADOS_INVALIDOS.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemaFactory.criar(CodigoErro.DADOS_INVALIDOS, request, erros));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> acessoNegado(AccessDeniedException ex, HttpServletRequest request) {
        return resposta(CodigoErro.ACESSO_NEGADO, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> naoAutenticado(AuthenticationException ex, HttpServletRequest request) {
        return resposta(CodigoErro.NAO_AUTENTICADO, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> erroInterno(Exception ex, HttpServletRequest request) {
        // O detalhe técnico fica só no log interno, ligado ao correlacaoId; a resposta é genérica.
        log.error("Erro não tratado. correlacaoId={}", CorrelacaoHttp.obter(request), ex);
        return resposta(CodigoErro.ERRO_INTERNO, request);
    }

    private ResponseEntity<Object> responderComErros(Exception ex, List<ErroCampo> erros, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatus(status);
        if (!erros.isEmpty()) {
            problema.setProperty("erros", erros);
        }
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    private static ResponseEntity<ProblemDetail> resposta(CodigoErro codigo, HttpServletRequest request) {
        return ResponseEntity.status(codigo.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemaFactory.criar(codigo, request));
    }

    @SuppressWarnings("unchecked")
    private static List<ErroCampo> errosExistentes(ProblemDetail problema) {
        Object erros = problema.getProperties() == null ? null : problema.getProperties().get("erros");
        return erros instanceof List<?> lista ? (List<ErroCampo>) lista : List.of();
    }

    private static HttpHeaders comTipoProblema(HttpHeaders headers) {
        HttpHeaders copia = new HttpHeaders();
        if (headers != null) {
            copia.putAll(headers);
        }
        copia.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return copia;
    }

    private static HttpServletRequest requisicao(WebRequest request) {
        return ((ServletWebRequest) request).getRequest();
    }

    private static boolean contemCausa(Throwable erro, Class<? extends Throwable> tipo) {
        for (Throwable t = erro; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (tipo.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static String ultimoNo(String caminho) {
        int ponto = caminho.lastIndexOf('.');
        return ponto >= 0 ? caminho.substring(ponto + 1) : caminho;
    }

    private static String mensagemSegura(String mensagem) {
        return mensagem == null || mensagem.isBlank() ? "valor inválido" : mensagem;
    }
}
