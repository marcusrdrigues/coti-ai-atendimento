package br.com.coti.conhecimento.api.rest.dto;

import br.com.coti.conhecimento.api.mcp.TrechoResultado;
import br.com.coti.conhecimento.aplicacao.ResultadoDaBusca;
import java.util.List;

/**
 * Resposta do diagnóstico (contrato, seção 3.5): o mesmo {@code ResultadoBusca} da ferramenta MCP, acrescido do
 * limiar aplicado, do modelo usado na consulta e da duração.
 */
public record BuscaDiagnosticoResponse(
        List<TrechoResultado> trechos,
        int total,
        double limiarSimilaridade,
        String modeloEmbedding,
        long duracaoMs) {

    public static BuscaDiagnosticoResponse de(ResultadoDaBusca resultado) {
        List<TrechoResultado> trechos = resultado.trechos().stream().map(TrechoResultado::de).toList();
        return new BuscaDiagnosticoResponse(trechos, trechos.size(), resultado.limiarSimilaridade(),
                resultado.modeloEmbedding(), resultado.duracao().toMillis());
    }
}
