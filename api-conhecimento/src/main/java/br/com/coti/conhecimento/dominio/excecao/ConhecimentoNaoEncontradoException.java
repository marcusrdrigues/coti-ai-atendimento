package br.com.coti.conhecimento.dominio.excecao;

import java.util.UUID;

/** Nenhum conhecimento visível ao gestor com o identificador informado. */
public class ConhecimentoNaoEncontradoException extends RuntimeException {

    private final UUID id;

    public ConhecimentoNaoEncontradoException(UUID id) {
        super("Conhecimento não encontrado");
        this.id = id;
    }

    public UUID id() {
        return id;
    }
}
