package br.com.coti.conhecimento.api.rest.dto;

import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.Pendencia;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Resposta de {@code POST /api/conhecimentos}: 201 (publicado) ou 202 (retido para revisão). */
public record ConhecimentoCadastradoResponse(
        UUID id,
        StatusDocumento status,
        int quantidadeChunks,
        String modeloEmbedding,
        OffsetDateTime cadastradoEm,
        List<Pendencia> pendencias) {

    public static ConhecimentoCadastradoResponse de(Documento documento) {
        return new ConhecimentoCadastradoResponse(documento.id(), documento.status(),
                documento.quantidadeChunks(), documento.modeloEmbedding(), documento.cadastradoEm(),
                documento.pendencias().isEmpty() ? null : documento.pendencias());
    }
}
