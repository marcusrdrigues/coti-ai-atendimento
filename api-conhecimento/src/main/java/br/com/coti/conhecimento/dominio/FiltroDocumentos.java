package br.com.coti.conhecimento.dominio;

/** Filtros opcionais da listagem; {@code null} significa "todos". */
public record FiltroDocumentos(StatusDocumento status, Categoria categoria) {

    public static FiltroDocumentos todos() {
        return new FiltroDocumentos(null, null);
    }
}
