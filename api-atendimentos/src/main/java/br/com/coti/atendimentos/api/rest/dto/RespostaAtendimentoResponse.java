package br.com.coti.atendimentos.api.rest.dto;

import br.com.coti.atendimentos.dominio.ResultadoAtendimento;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Resposta de {@code POST /api/atendimentos/perguntar} (contrato, seção 5.1). Nunca traz prompt, nome do modelo,
 * tokens, texto integral dos trechos nem trechos não citados. {@code similaridade} aparece como {@code null}
 * quando a busca não a fornece (campo obrigatório do contrato).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RespostaAtendimentoResponse(
        String atendimentoId,
        String resposta,
        boolean conhecimentoSuficiente,
        List<FonteResponse> fontes,
        OffsetDateTime geradoEm) {

    public static RespostaAtendimentoResponse de(String atendimentoId, ResultadoAtendimento resultado) {
        return new RespostaAtendimentoResponse(atendimentoId, resultado.resposta(), resultado.conhecimentoSuficiente(),
                resultado.fontes().stream().map(FonteResponse::de).toList(), resultado.geradoEm());
    }
}
