package br.com.coti.conhecimento.infraestrutura.indexacao;

import br.com.coti.conhecimento.aplicacao.porta.IndexacaoDocumentoPort;
import br.com.coti.conhecimento.dominio.Documento;
import org.springframework.stereotype.Component;

/**
 * Adaptador provisório da Fase 3: a divisão em trechos e os embeddings só existem a partir da Fase 4 (T035).
 * Falha sempre, de modo que um cadastro sem pendências termina em {@code ERRO_PROCESSAMENTO} (503) em vez de
 * parecer publicado sem trechos. Deve ser removido quando o adaptador real for criado.
 */
@Component
public final class IndexadorNaoImplementado implements IndexacaoDocumentoPort {

    @Override
    public int indexar(Documento documento) {
        throw new UnsupportedOperationException("Indexação disponível a partir da Fase 4");
    }
}
