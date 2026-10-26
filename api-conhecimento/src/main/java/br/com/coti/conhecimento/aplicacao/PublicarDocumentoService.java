package br.com.coti.conhecimento.aplicacao;

import br.com.coti.conhecimento.aplicacao.porta.RepositorioDocumentoPort;
import br.com.coti.conhecimento.aplicacao.porta.RepositorioVetorialPort;
import br.com.coti.conhecimento.dominio.Documento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publicação atômica (ADR-07): numa única transação curta, os trechos do documento passam de
 * {@code PROCESSANDO} a {@code PUBLICADO} e o documento também. Se qualquer passo falhar, nada muda e os
 * trechos continuam invisíveis à busca. Não é {@code final} porque o proxy transacional exige.
 */
@Service
@Transactional
public class PublicarDocumentoService {

    private final RepositorioDocumentoPort documentos;
    private final RepositorioVetorialPort vetorial;

    public PublicarDocumentoService(RepositorioDocumentoPort documentos, RepositorioVetorialPort vetorial) {
        this.documentos = documentos;
        this.vetorial = vetorial;
    }

    public Documento publicar(Documento processando, int trechosGravados) {
        Documento publicado = processando.publicar(trechosGravados);
        int promovidos = vetorial.promoverTrechos(processando.id());
        if (promovidos != trechosGravados) {
            // Reverte a transação: publicar com a quantidade errada deixaria a base inconsistente.
            throw new IllegalStateException("trechos promovidos (" + promovidos + ") diferem dos gravados ("
                    + trechosGravados + ")");
        }
        documentos.atualizarEstado(publicado);
        return publicado;
    }
}
