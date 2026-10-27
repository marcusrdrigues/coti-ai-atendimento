package br.com.coti.conhecimento.dominio;

import java.util.UUID;

/**
 * Trecho devolvido pela busca semântica (RF04). Só carrega o que o contrato permite sair: nada de vetor, status,
 * modelo de embedding nem metadados internos.
 *
 * @param similaridade cosseno entre a pergunta e o trecho, de 0 a 1; {@code null} se o mecanismo de busca não a fornecer
 */
public record TrechoEncontrado(
        UUID id,
        String texto,
        UUID documentoId,
        String titulo,
        String fonte,
        Categoria categoria,
        Double similaridade) {

    /** Mesmo trecho com o texto cortado em {@code limite} caracteres. */
    public TrechoEncontrado comTextoLimitadoA(int limite) {
        if (texto.length() <= limite) {
            return this;
        }
        return new TrechoEncontrado(id, texto.substring(0, limite), documentoId, titulo, fonte, categoria, similaridade);
    }
}
