package br.com.coti.conhecimento.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Visão de listagem de um documento: tudo, menos o conteúdo. */
public record DocumentoResumo(
        UUID id,
        String titulo,
        String fonte,
        Categoria categoria,
        StatusDocumento status,
        int quantidadeChunks,
        LocalDate vigenteAte,
        OffsetDateTime cadastradoEm) {
}
