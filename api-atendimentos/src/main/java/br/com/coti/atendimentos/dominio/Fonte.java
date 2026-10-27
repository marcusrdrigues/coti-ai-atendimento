package br.com.coti.atendimentos.dominio;

import java.util.UUID;

/** Fonte citada na resposta. Todos os campos são copiados do trecho da ferramenta MCP, nunca do modelo. */
public record Fonte(UUID id, UUID documentoId, String titulo, String fonte, Categoria categoria, Double similaridade) {

    public static Fonte de(TrechoConhecimento trecho) {
        return new Fonte(trecho.id(), trecho.documentoId(), trecho.titulo(), trecho.fonte(), trecho.categoria(),
                trecho.similaridade());
    }
}
