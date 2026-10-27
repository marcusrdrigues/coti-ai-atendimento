package br.com.coti.conhecimento.api.mcp;

import br.com.coti.conhecimento.aplicacao.ResultadoDaBusca;
import java.util.List;

/** Saída da ferramenta {@code buscar_conhecimento} (specs/06-api-contracts.md §4.2). Lista vazia é sucesso. */
public record ResultadoBusca(List<TrechoResultado> trechos, int total) {

    public static ResultadoBusca de(ResultadoDaBusca resultado) {
        List<TrechoResultado> trechos = resultado.trechos().stream().map(TrechoResultado::de).toList();
        return new ResultadoBusca(trechos, trechos.size());
    }
}
