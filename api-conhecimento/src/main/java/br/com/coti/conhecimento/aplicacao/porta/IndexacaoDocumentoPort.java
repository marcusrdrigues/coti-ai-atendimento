package br.com.coti.conhecimento.aplicacao.porta;

import br.com.coti.conhecimento.dominio.Documento;

/**
 * Transforma um documento aceito em trechos pesquisáveis (divisão, embeddings e gravação no pgvector).
 * A implementação real chega na Fase 4; até lá existe um adaptador que sempre falha.
 */
public interface IndexacaoDocumentoPort {

    /**
     * Indexa o documento e devolve a quantidade de trechos gravados (maior que zero). Em qualquer falha não
     * pode restar trecho do documento gravado.
     */
    int indexar(Documento documento);
}
