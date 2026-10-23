package br.com.coti.conhecimento.dominio;

import java.util.List;

/** Resultado da análise do texto de um documento antes de qualquer indexação. */
public record ResultadoAnalise(boolean contemDadoPessoal, List<Pendencia> pendencias) {

    public ResultadoAnalise {
        pendencias = List.copyOf(pendencias);
    }

    public static ResultadoAnalise limpo() {
        return new ResultadoAnalise(false, List.of());
    }
}
