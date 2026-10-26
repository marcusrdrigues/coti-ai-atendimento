package br.com.coti.conhecimento.aplicacao.porta;

import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.Trecho;
import java.util.List;
import java.util.UUID;

/**
 * Trechos pesquisáveis no banco vetorial (tabela {@code vector_store}). Gravar um lote gera os embeddings;
 * por isso as falhas do serviço de embeddings aparecem aqui, já classificadas em transitórias e definitivas.
 */
public interface RepositorioVetorialPort {

    /**
     * Gera os embeddings do lote e o grava com o status {@code PROCESSANDO} (invisível para a busca). Idempotente:
     * regravar um trecho com o mesmo id atualiza a linha existente.
     *
     * @throws br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingTransitoriaException se vale tentar de novo
     * @throws br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingException para falha definitiva
     */
    void gravarLote(Documento documento, List<Trecho> lote);

    /** Torna {@code PUBLICADO} todos os trechos {@code PROCESSANDO} do documento; devolve quantos mudaram. */
    int promoverTrechos(UUID documentoId);

    /** Apaga todos os trechos do documento, em qualquer status. */
    void removerPorDocumento(UUID documentoId);
}
