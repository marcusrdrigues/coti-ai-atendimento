package br.com.coti.atendimentos.dominio.excecao;

/** O modelo de IA não pôde gerar a resposta (indisponível, limite atingido, credencial recusada ou tempo esgotado). */
public class ModeloIndisponivelException extends RuntimeException {

    public ModeloIndisponivelException(String motivo, Throwable causa) {
        super(motivo, causa);
    }

    public ModeloIndisponivelException(String motivo) {
        super(motivo);
    }
}
