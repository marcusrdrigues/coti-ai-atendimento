package br.com.coti.conhecimento.infraestrutura.vetorial;

import br.com.coti.conhecimento.aplicacao.porta.BuscaVetorialPort;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import br.com.coti.conhecimento.dominio.TrechoEncontrado;
import br.com.coti.conhecimento.dominio.VigenciaMetadado;
import br.com.coti.conhecimento.dominio.excecao.BaseConhecimentoIndisponivelException;
import br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingException;
import br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingTransitoriaException;
import br.com.coti.conhecimento.dominio.excecao.ModeloIndisponivelException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Busca no pgvector pelo {@link VectorStore} do Spring AI. O filtro (publicado, modelo atual, vigente) é montado
 * com o construtor de expressões a partir de constantes e dos parâmetros já validados, nunca por concatenação
 * de texto, e vai dentro da consulta vetorial. O score devolvido é a similaridade de cosseno de 0 a 1 (spike T037).
 */
@Component
public final class PgVectorBuscaAdapter implements BuscaVetorialPort {

    private final VectorStore vectorStore;

    public PgVectorBuscaAdapter(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<TrechoEncontrado> buscar(Consulta consulta) {
        List<Document> documentos;
        try {
            documentos = vectorStore.similaritySearch(requisicao(consulta));
        } catch (RuntimeException falha) {
            throw traduzir(falha);
        }
        return documentos.stream().map(PgVectorBuscaAdapter::paraTrecho).toList();
    }

    private static SearchRequest requisicao(Consulta consulta) {
        var f = new FilterExpressionBuilder();
        var filtro = f.and(
                f.and(f.eq("status", StatusDocumento.PUBLICADO.name()), f.eq("modeloEmbedding", consulta.modeloEmbedding())),
                f.gte("vigenteAte", VigenciaMetadado.paraMetadado(consulta.hoje()))).build();
        return SearchRequest.builder()
                .query(consulta.pergunta())
                .topK(consulta.limite())
                .similarityThreshold(consulta.limiarSimilaridade())
                .filterExpression(filtro)
                .build();
    }

    private static RuntimeException traduzir(RuntimeException falha) {
        RuntimeException traduzida = TradutorFalhasEmbedding.traduzir(falha);
        if (traduzida instanceof FalhaEmbeddingTransitoriaException || traduzida instanceof FalhaEmbeddingException) {
            return new ModeloIndisponivelException(traduzida);
        }
        if (traduzida instanceof DataAccessException) {
            return new BaseConhecimentoIndisponivelException(traduzida);
        }
        return traduzida;
    }

    private static TrechoEncontrado paraTrecho(Document documento) {
        Map<String, Object> metadados = documento.getMetadata();
        return new TrechoEncontrado(
                UUID.fromString(documento.getId()),
                documento.getText(),
                UUID.fromString(texto(metadados, "documentoId")),
                texto(metadados, "titulo"),
                texto(metadados, "fonte"),
                Categoria.valueOf(texto(metadados, "categoria")),
                similaridade(documento.getScore()));
    }

    /** O cosseno pode passar de 1 por arredondamento de ponto flutuante; o contrato vai de 0 a 1. */
    private static Double similaridade(Double score) {
        return score == null ? null : Math.max(0.0, Math.min(1.0, score));
    }

    private static String texto(Map<String, Object> metadados, String chave) {
        Object valor = metadados.get(chave);
        if (valor == null) {
            throw new IllegalStateException("Trecho sem o metadado obrigatório '" + chave + "'.");
        }
        return valor.toString();
    }
}
