package br.com.coti.conhecimento.dominio.excecao;

/**
 * Falha momentânea do serviço de embeddings (tempo esgotado, conexão, limite de requisições, erro 5xx): vale
 * tentar de novo. É a única falha que a política de novas tentativas repete.
 */
public class FalhaEmbeddingTransitoriaException extends RuntimeException {

    public FalhaEmbeddingTransitoriaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
