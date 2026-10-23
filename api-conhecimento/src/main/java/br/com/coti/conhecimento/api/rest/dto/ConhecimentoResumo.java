package br.com.coti.conhecimento.api.rest.dto;

import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.DocumentoResumo;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Item da listagem de conhecimentos; nunca traz o conteúdo. */
public record ConhecimentoResumo(
        UUID id,
        String titulo,
        String fonte,
        Categoria categoria,
        StatusDocumento status,
        int quantidadeChunks,
        LocalDate vigenteAte,
        OffsetDateTime cadastradoEm) {

    public static ConhecimentoResumo de(DocumentoResumo documento) {
        return new ConhecimentoResumo(documento.id(), documento.titulo(), documento.fonte(),
                documento.categoria(), documento.status(), documento.quantidadeChunks(),
                documento.vigenteAte(), documento.cadastradoEm());
    }
}
