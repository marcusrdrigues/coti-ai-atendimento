package br.com.coti.conhecimento.dominio;

import java.util.Set;

/**
 * Estado de um documento (specs/03-architecture.md §6.5).
 *
 * <ul>
 *   <li>{@code PROCESSANDO}: interno e transitório, dura só durante o cadastro; não é pesquisável nem
 *       aparece na API;</li>
 *   <li>{@code PUBLICADO}: processado com sucesso; seus trechos são pesquisáveis;</li>
 *   <li>{@code EM_REVISAO}: retido por possível prompt injection; sem trechos;</li>
 *   <li>{@code ERRO_PROCESSAMENTO}: falha na divisão ou nos embeddings; sem trechos.</li>
 * </ul>
 * Só {@code PROCESSANDO} muda de estado; os demais são finais (correção é um novo cadastro).
 */
public enum StatusDocumento {
    PROCESSANDO,
    PUBLICADO,
    EM_REVISAO,
    ERRO_PROCESSAMENTO;

    public Set<StatusDocumento> destinosPermitidos() {
        return this == PROCESSANDO ? Set.of(PUBLICADO, ERRO_PROCESSAMENTO) : Set.of();
    }

    public boolean podeIrPara(StatusDocumento destino) {
        return destinosPermitidos().contains(destino);
    }

    /** Estados que o gestor enxerga pela API. */
    public boolean visivelAoGestor() {
        return this != PROCESSANDO;
    }
}
