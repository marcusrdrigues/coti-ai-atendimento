package br.com.coti.conhecimento.api.rest;

import br.com.coti.conhecimento.api.erro.CodigoErro;
import br.com.coti.conhecimento.api.erro.ErroCampo;
import br.com.coti.conhecimento.api.erro.ProblemaFactory;
import br.com.coti.conhecimento.dominio.excecao.ArgumentosInvalidosException;
import br.com.coti.conhecimento.dominio.excecao.BaseConhecimentoIndisponivelException;
import br.com.coti.conhecimento.dominio.excecao.ConhecimentoDuplicadoException;
import br.com.coti.conhecimento.dominio.excecao.ConhecimentoNaoEncontradoException;
import br.com.coti.conhecimento.dominio.excecao.DadoPessoalDetectadoException;
import br.com.coti.conhecimento.dominio.excecao.DadosInvalidosException;
import br.com.coti.conhecimento.dominio.excecao.DocumentoGrandeDemaisException;
import br.com.coti.conhecimento.dominio.excecao.IndexacaoFalhouException;
import br.com.coti.conhecimento.dominio.excecao.ModeloIndisponivelException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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

    @ExceptionHandler(DocumentoGrandeDemaisException.class)
    public ResponseEntity<ProblemDetail> documentoGrandeDemais(DocumentoGrandeDemaisException ex, HttpServletRequest request) {
        return resposta(CodigoErro.CONTEUDO_GRANDE_DEMAIS, request, ProblemaFactory.criar(
                CodigoErro.CONTEUDO_GRANDE_DEMAIS, request));
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
        // Falha do banco durante a indexação é indisponibilidade da base; as demais vêm do serviço de embeddings.
        CodigoErro codigo = causadaPorBanco(ex) ? CodigoErro.BASE_CONHECIMENTO_INDISPONIVEL : CodigoErro.MODELO_INDISPONIVEL;
        ProblemDetail problema = ProblemaFactory.criar(codigo, request);
        problema.setProperty("conhecimentoId", ex.conhecimentoId().toString());
        return resposta(codigo, request, problema);
    }

    @ExceptionHandler(ArgumentosInvalidosException.class)
    public ResponseEntity<ProblemDetail> argumentosInvalidos(ArgumentosInvalidosException ex,
                                                             HttpServletRequest request) {
        return resposta(CodigoErro.DADOS_INVALIDOS, request, ProblemaFactory.criar(CodigoErro.DADOS_INVALIDOS, request,
                List.of(new ErroCampo(ex.campo(), ex.getMessage()))));
    }

    @ExceptionHandler(ModeloIndisponivelException.class)
    public ResponseEntity<ProblemDetail> modeloIndisponivel(ModeloIndisponivelException ex, HttpServletRequest request) {
        log.error("Embedding da pergunta indisponível.", ex);
        return resposta(CodigoErro.MODELO_INDISPONIVEL, request, ProblemaFactory.criar(
                CodigoErro.MODELO_INDISPONIVEL, request));
    }

    @ExceptionHandler(BaseConhecimentoIndisponivelException.class)
    public ResponseEntity<ProblemDetail> baseIndisponivel(BaseConhecimentoIndisponivelException ex,
                                                          HttpServletRequest request) {
        log.error("Base de conhecimento indisponível na busca.", ex);
        return resposta(CodigoErro.BASE_CONHECIMENTO_INDISPONIVEL, request, ProblemaFactory.criar(
                CodigoErro.BASE_CONHECIMENTO_INDISPONIVEL, request));
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class})
    public ResponseEntity<ProblemDetail> bancoIndisponivel(RuntimeException ex, HttpServletRequest request) {
        log.error("Banco de dados indisponível.", ex);
        return resposta(CodigoErro.BASE_CONHECIMENTO_INDISPONIVEL, request, ProblemaFactory.criar(
                CodigoErro.BASE_CONHECIMENTO_INDISPONIVEL, request));
    }

    private static boolean causadaPorBanco(Throwable falha) {
        for (Throwable t = falha; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof DataAccessResourceFailureException || t instanceof TransientDataAccessException) {
                return true;
            }
        }
        return false;
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
