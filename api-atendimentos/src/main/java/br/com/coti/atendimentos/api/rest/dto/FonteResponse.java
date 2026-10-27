package br.com.coti.atendimentos.api.rest.dto;

import br.com.coti.atendimentos.dominio.Categoria;
import br.com.coti.atendimentos.dominio.Fonte;
import java.util.UUID;

/** Fonte citada, copiada do resultado da ferramenta MCP (contrato, seção 5.1). */
public record FonteResponse(UUID id, UUID documentoId, String titulo, String fonte, Categoria categoria,
                            Double similaridade) {

    public static FonteResponse de(Fonte fonte) {
        return new FonteResponse(fonte.id(), fonte.documentoId(), fonte.titulo(), fonte.fonte(), fonte.categoria(),
                fonte.similaridade());
    }
}
