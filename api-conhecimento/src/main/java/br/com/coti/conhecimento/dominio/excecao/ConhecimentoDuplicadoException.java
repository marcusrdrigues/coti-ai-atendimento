package br.com.coti.conhecimento.dominio.excecao;

/** Já existe um conhecimento com o mesmo conteúdo (mesmo hash do texto normalizado). */
public class ConhecimentoDuplicadoException extends RuntimeException {

    public ConhecimentoDuplicadoException() {
        super("Já existe um conhecimento com o mesmo conteúdo");
    }
}
