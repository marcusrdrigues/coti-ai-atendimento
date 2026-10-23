package br.com.coti.conhecimento.api.rest.dto;

import br.com.coti.conhecimento.dominio.DocumentoResumo;
import br.com.coti.conhecimento.dominio.Pagina;
import java.util.List;

/** Resposta de {@code GET /api/conhecimentos}. */
public record PaginaConhecimentosResponse(
        List<ConhecimentoResumo> itens,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas) {

    public static PaginaConhecimentosResponse de(Pagina<DocumentoResumo> pagina) {
        return new PaginaConhecimentosResponse(
                pagina.itens().stream().map(ConhecimentoResumo::de).toList(),
                pagina.pagina(), pagina.tamanho(), pagina.totalItens(), pagina.totalPaginas());
    }
}
