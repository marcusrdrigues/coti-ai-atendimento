package br.com.coti.conhecimento.dominio.excecao;

/** O banco da base de conhecimento não respondeu à busca. A causa técnica fica anexada só para o log interno. */
public class BaseConhecimentoIndisponivelException extends RuntimeException {

    public BaseConhecimentoIndisponivelException(Throwable causa) {
        super("Base de conhecimento indisponível no momento.", causa);
    }
}
