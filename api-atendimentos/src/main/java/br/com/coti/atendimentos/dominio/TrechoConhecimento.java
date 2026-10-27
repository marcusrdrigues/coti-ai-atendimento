package br.com.coti.atendimentos.dominio;

import java.util.UUID;

/**
 * Trecho devolvido pela ferramenta {@code buscar_conhecimento} (contrato, seção 4.2), já validado.
 *
 * @param similaridade cosseno de 0 a 1; {@code null} quando a busca não o fornece
 */
public record TrechoConhecimento(
        UUID id,
        String texto,
        UUID documentoId,
        String titulo,
        String fonte,
        Categoria categoria,
        Double similaridade) {

    /** Só o tamanho: o texto do trecho não vai para log. */
    @Override
    public String toString() {
        return "TrechoConhecimento[id=" + id + ", tamanho=" + texto.length() + "]";
    }
}
