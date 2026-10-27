package br.com.coti.conhecimento.dominio.excecao;

/**
 * Pergunta ou limite fora das regras da busca. A mensagem é segura para chegar ao cliente: cita o campo e a
 * regra, nunca o valor recebido.
 */
public class ArgumentosInvalidosException extends RuntimeException {

    private final String campo;

    public ArgumentosInvalidosException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
