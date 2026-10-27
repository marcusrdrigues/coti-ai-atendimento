package br.com.coti.conhecimento.api.mcp;

import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.TrechoEncontrado;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Trecho como o contrato o publica (specs/06-api-contracts.md §4.2). É a mesma forma na ferramenta MCP e no
 * diagnóstico REST. Só tem o que pode sair: nada de vetor, status, modelo de embedding nem metadados internos.
 */
public record TrechoResultado(
        UUID id,
        String texto,
        UUID documentoId,
        String titulo,
        String fonte,
        Categoria categoria,
        @JsonProperty(required = false) Double similaridade) {

    public static TrechoResultado de(TrechoEncontrado trecho) {
        return new TrechoResultado(trecho.id(), trecho.texto(), trecho.documentoId(), trecho.titulo(), trecho.fonte(),
                trecho.categoria(), trecho.similaridade());
    }
}
