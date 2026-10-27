package br.com.coti.conhecimento.dominio.excecao;

/**
 * O embedding da pergunta não pôde ser gerado (provedor fora do ar, limite atingido, credencial recusada ou tempo
 * esgotado). A causa técnica fica anexada só para o log interno.
 */
public class ModeloIndisponivelException extends RuntimeException {

    public ModeloIndisponivelException(Throwable causa) {
        super("Serviço de embeddings indisponível no momento.", causa);
    }
}
