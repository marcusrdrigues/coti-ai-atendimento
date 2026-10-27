package br.com.coti.atendimentos.api.rest;

import br.com.coti.atendimentos.api.erro.CodigoErro;
import br.com.coti.atendimentos.api.erro.CorrelacaoHttp;
import br.com.coti.atendimentos.api.erro.ErroCampo;
import br.com.coti.atendimentos.api.erro.ProblemaFactory;
import br.com.coti.atendimentos.dominio.excecao.BaseConhecimentoIndisponivelException;
import br.com.coti.atendimentos.dominio.excecao.ModeloIndisponivelException;
import br.com.coti.atendimentos.dominio.excecao.PerguntaInvalidaException;
import br.com.coti.atendimentos.dominio.excecao.TempoEsgotadoException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz as falhas de dependência do atendimento para os códigos do contrato (seção 2.2): 503 com
 * {@code Retry-After}, sem detalhe interno. O detalhe técnico vai só para o log, ligado ao id de correlação.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManipuladorExcecoesAtendimento {

    private static final Logger log = LoggerFactory.getLogger(ManipuladorExcecoesAtendimento.class);

    private final long retryAfterSegundos;

    public ManipuladorExcecoesAtendimento(
            @Value("${coti.atendimentos.resiliencia.retry-after:30s}") Duration retryAfter) {
        this.retryAfterSegundos = retryAfter.toSeconds();
    }

    /** Só ocorre se a pergunta ficar fora de 3 a 1.000 caracteres depois de remover espaços (a anotação não remove). */
    @ExceptionHandler(PerguntaInvalidaException.class)
    public ResponseEntity<ProblemDetail> perguntaInvalida(PerguntaInvalidaException ex, HttpServletRequest request) {
        return resposta(CodigoErro.DADOS_INVALIDOS, ProblemaFactory.criar(CodigoErro.DADOS_INVALIDOS, request,
                List.of(new ErroCampo("pergunta", "deve ter entre 3 e 1.000 caracteres"))));
    }

    @ExceptionHandler(BaseConhecimentoIndisponivelException.class)
    public ResponseEntity<ProblemDetail> baseIndisponivel(BaseConhecimentoIndisponivelException ex,
                                                          HttpServletRequest request) {
        log.error("Base de conhecimento indisponível no atendimento. correlacaoId={} motivo={}",
                CorrelacaoHttp.obter(request), ex.getMessage(), ex);
        return resposta(CodigoErro.BASE_CONHECIMENTO_INDISPONIVEL,
                ProblemaFactory.criar(CodigoErro.BASE_CONHECIMENTO_INDISPONIVEL, request));
    }

    @ExceptionHandler(ModeloIndisponivelException.class)
    public ResponseEntity<ProblemDetail> modeloIndisponivel(ModeloIndisponivelException ex, HttpServletRequest request) {
        log.error("Modelo indisponível no atendimento. correlacaoId={} motivo={}", CorrelacaoHttp.obter(request),
                ex.getMessage(), ex);
        return resposta(CodigoErro.MODELO_INDISPONIVEL,
                ProblemaFactory.criar(CodigoErro.MODELO_INDISPONIVEL, request));
    }

    @ExceptionHandler(TempoEsgotadoException.class)
    public ResponseEntity<ProblemDetail> tempoEsgotado(TempoEsgotadoException ex, HttpServletRequest request) {
        log.warn("Tempo total do atendimento excedido. correlacaoId={}", CorrelacaoHttp.obter(request));
        return resposta(CodigoErro.TEMPO_ESGOTADO, ProblemaFactory.criar(CodigoErro.TEMPO_ESGOTADO, request));
    }

    private ResponseEntity<ProblemDetail> resposta(CodigoErro codigo, ProblemDetail problema) {
        ResponseEntity.BodyBuilder resposta = ResponseEntity.status(codigo.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (codigo.status().value() == 503) {
            resposta.header("Retry-After", String.valueOf(retryAfterSegundos));
        }
        return resposta.body(problema);
    }
}
