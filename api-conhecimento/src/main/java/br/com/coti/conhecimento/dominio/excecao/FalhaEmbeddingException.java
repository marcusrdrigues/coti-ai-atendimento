package br.com.coti.conhecimento.dominio.excecao;

/**
 * Falha definitiva do serviço de embeddings (credencial recusada, requisição inválida, resposta inesperada):
 * repetir não adianta. Não repete nem vira "indisponibilidade temporária" para quem chamou.
 */
public class FalhaEmbeddingException extends RuntimeException {

    public FalhaEmbeddingException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
