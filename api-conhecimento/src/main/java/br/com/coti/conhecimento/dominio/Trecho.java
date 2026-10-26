package br.com.coti.conhecimento.dominio;

import java.util.UUID;

/** Parte de um documento com identificador determinístico e posição (a partir de zero). */
public record Trecho(UUID id, int ordem, String texto) {

    public Trecho {
        if (id == null || texto == null || texto.isBlank() || ordem < 0) {
            throw new IllegalArgumentException("trecho inválido");
        }
    }
}
