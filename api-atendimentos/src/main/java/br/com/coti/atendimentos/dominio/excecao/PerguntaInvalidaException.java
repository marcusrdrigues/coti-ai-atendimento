package br.com.coti.atendimentos.dominio.excecao;

/** A pergunta não tem entre 3 e 1.000 caracteres. A mensagem não traz o valor recebido. */
public class PerguntaInvalidaException extends RuntimeException {

    public PerguntaInvalidaException() {
        super("A pergunta deve ter entre 3 e 1.000 caracteres.");
    }
}
