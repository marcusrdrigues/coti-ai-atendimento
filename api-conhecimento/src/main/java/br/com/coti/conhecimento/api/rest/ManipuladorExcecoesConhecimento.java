package br.com.coti.conhecimento.api.rest;

import br.com.coti.conhecimento.api.erro.CodigoErro;
import br.com.coti.conhecimento.api.erro.ErroCampo;
import br.com.coti.conhecimento.api.erro.ProblemaFactory;
import br.com.coti.conhecimento.dominio.excecao.ConhecimentoDuplicadoException;
import br.com.coti.conhecimento.dominio.excecao.ConhecimentoNaoEncontradoException;
import br.com.coti.conhecimento.dominio.excecao.DadoPessoalDetectadoException;
import br.com.coti.conhecimento.dominio.excecao.DadosInvalidosException;
import br.com.coti.conhecimento.dominio.excecao.IndexacaoFalhouException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduz as exceções de domínio do conhecimento para os códigos de erro do contrato (seção 2.2). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManipuladorExcecoesConhecimento {

    private static final Logger log = LoggerFactory.getLogger(ManipuladorExcecoesConhecimento.class);
    private static final String RETRY_AFTER_SEGUNDOS = "30";

    @ExceptionHandler(ConhecimentoDuplicadoException.class)
    public ResponseEntity<ProblemDetail> duplicado(ConhecimentoDuplicadoException ex, HttpServletRequest request) {
        return resposta(CodigoErro.CONHECIMENTO_DUPLICADO, request, ProblemaFactory.criar(
                CodigoErro.CONHECIMENTO_DUPLICADO, request));
    }

    @ExceptionHandler(DadoPessoalDetectadoException.class)
    public ResponseEntity<ProblemDetail> dadoPessoal(DadoPessoalDetectadoException ex, HttpServletRequest request) {
        return resposta(CodigoErro.DADO_PESSOAL_DETECTADO, request, ProblemaFactory.criar(
                CodigoErro.DADO_PESSOAL_DETECTADO, request));
    }

    @ExceptionHandler(ConhecimentoNaoEncontradoException.class)
    public ResponseEntity<ProblemDetail> naoEncontrado(ConhecimentoNaoEncontradoException ex,
                                                       HttpServletRequest request) {
        return resposta(CodigoErro.CONHECIMENTO_NAO_ENCONTRADO, request, ProblemaFactory.criar(
                CodigoErro.CONHECIMENTO_NAO_ENCONTRADO, request));
    }

    @ExceptionHandler(DadosInvalidosException.class)
    public ResponseEntity<ProblemDetail> dadosInvalidos(DadosInvalidosException ex, HttpServletRequest request) {
        var erros = ex.campos().stream().map(c -> new ErroCampo(c.campo(), c.mensagem())).toList();
        return resposta(CodigoErro.DADOS_INVALIDOS, request,
                ProblemaFactory.criar(CodigoErro.DADOS_INVALIDOS, request, erros));
    }

    /** O documento ficou {@code ERRO_PROCESSAMENTO}; o gestor o consulta pelo {@code conhecimentoId}. */
    @ExceptionHandler(IndexacaoFalhouException.class)
    public ResponseEntity<ProblemDetail> indexacaoFalhou(IndexacaoFalhouException ex, HttpServletRequest request) {
        ProblemDetail problema = ProblemaFactory.criar(CodigoErro.MODELO_INDISPONIVEL, request);
        problema.setProperty("conhecimentoId", ex.conhecimentoId().toString());
        return resposta(CodigoErro.MODELO_INDISPONIVEL, request, problema);
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class})
    public ResponseEntity<ProblemDetail> bancoIndisponivel(RuntimeException ex, HttpServletRequest request) {
        log.error("Banco de dados indisponível.", ex);
        return resposta(CodigoErro.BASE_CONHECIMENTO_INDISPONIVEL, request, ProblemaFactory.criar(
                CodigoErro.BASE_CONHECIMENTO_INDISPONIVEL, request));
    }

    private static ResponseEntity<ProblemDetail> resposta(CodigoErro codigo, HttpServletRequest request,
                                                          ProblemDetail problema) {
        ResponseEntity.BodyBuilder resposta = ResponseEntity.status(codigo.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (codigo.status().value() == 503) {
            resposta.header("Retry-After", RETRY_AFTER_SEGUNDOS);
        }
        return resposta.body(problema);
    }
}
