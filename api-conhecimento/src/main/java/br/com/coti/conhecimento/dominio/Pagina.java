package br.com.coti.conhecimento.dominio;

import java.util.List;

/** Página de resultados. {@code pagina} começa em zero. */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long totalItens) {

    public Pagina {
        itens = List.copyOf(itens);
        if (pagina < 0 || tamanho < 1 || totalItens < 0) {
            throw new IllegalArgumentException("página inválida");
        }
    }

    public int totalPaginas() {
        return (int) ((totalItens + tamanho - 1) / tamanho);
    }
}
