package br.com.coti.conhecimento.api.rest.dto;

import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.Pendencia;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Resposta de {@code GET /api/conhecimentos/{id}}: o resumo mais conteúdo, modelo, autor e pendências. */
public record ConhecimentoDetalheResponse(
        UUID id,
        String titulo,
        String conteudo,
        String fonte,
        Categoria categoria,
        StatusDocumento status,
        int quantidadeChunks,
        String modeloEmbedding,
        LocalDate vigenteAte,
        OffsetDateTime cadastradoEm,
        String cadastradoPor,
        List<Pendencia> pendencias) {

    public static ConhecimentoDetalheResponse de(Documento documento) {
        return new ConhecimentoDetalheResponse(documento.id(), documento.titulo(), documento.conteudo(),
                documento.fonte(), documento.categoria(), documento.status(), documento.quantidadeChunks(),
                documento.modeloEmbedding(), documento.vigenteAte(), documento.cadastradoEm(),
                documento.cadastradoPor(), documento.pendencias().isEmpty() ? null : documento.pendencias());
    }
}
