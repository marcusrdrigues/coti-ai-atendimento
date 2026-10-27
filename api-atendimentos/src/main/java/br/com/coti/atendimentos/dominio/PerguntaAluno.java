package br.com.coti.atendimentos.dominio;

import br.com.coti.atendimentos.dominio.excecao.PerguntaInvalidaException;

/**
 * Pergunta do aluno, já sem espaços nas pontas, com 3 a 1.000 caracteres (RF06). Pode conter dado pessoal, então
 * o texto nunca aparece em {@code toString}, em log nem em mensagem de erro.
 */
public record PerguntaAluno(String texto) {

    public static final int TAMANHO_MINIMO = 3;
    public static final int TAMANHO_MAXIMO = 1_000;

    public PerguntaAluno {
        texto = texto == null ? "" : texto.strip();
        if (texto.length() < TAMANHO_MINIMO || texto.length() > TAMANHO_MAXIMO) {
            throw new PerguntaInvalidaException();
        }
    }

    @Override
    public String toString() {
        return "PerguntaAluno[tamanho=" + texto.length() + "]";
    }
}
