package br.com.coti.atendimentos.dominio.excecao;

/**
 * A base de conhecimento não pôde responder: timeout, conexão recusada, credencial recusada, {@code isError} ou
 * resultado fora do contrato. A causa técnica fica anexada só para o log interno.
 */
public class BaseConhecimentoIndisponivelException extends RuntimeException {

    public BaseConhecimentoIndisponivelException(String motivo, Throwable causa) {
        super(motivo, causa);
    }

    public BaseConhecimentoIndisponivelException(String motivo) {
        super(motivo);
    }
}
