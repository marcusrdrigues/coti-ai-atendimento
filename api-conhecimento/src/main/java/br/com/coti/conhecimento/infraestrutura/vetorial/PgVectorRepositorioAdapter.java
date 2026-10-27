package br.com.coti.conhecimento.infraestrutura.vetorial;

import br.com.coti.conhecimento.aplicacao.porta.RepositorioVetorialPort;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import br.com.coti.conhecimento.dominio.Trecho;
import br.com.coti.conhecimento.dominio.VigenciaMetadado;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.ContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Adaptador do banco vetorial com o {@link VectorStore} do Spring AI (PgVectorStore). A gravação gera os embeddings
 * do texto dos trechos (os metadados não entram no embedding). Publicar um documento muda o metadado
 * {@code status} dos seus trechos por SQL parametrizado, para que isso aconteça na mesma transação da tabela
 * {@code documento}.
 */
@Component
public final class PgVectorRepositorioAdapter implements RepositorioVetorialPort {

    /**
     * O que o modelo de embedding recebe de cada trecho: somente o texto. O padrão do Spring AI no modelo da OpenAI
     * acrescenta os metadados (ids, status, datas) e um prefixo de quebras de linha, o que polui o vetor; o teste
     * entre os dois serviços (T053) mostrou isso. Vale para qualquer modo de metadados configurado.
     */
    static final ContentFormatter SOMENTE_O_TEXTO = (documento, modo) -> documento.getText();

    static final String CHAVE_DOCUMENTO_ID = "documentoId";
    static final String CHAVE_STATUS = "status";

    private final VectorStore vectorStore;
    private final JdbcClient jdbc;

    public PgVectorRepositorioAdapter(VectorStore vectorStore, JdbcClient jdbc) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
    }

    @Override
    public void gravarLote(Documento documento, List<Trecho> lote) {
        List<Document> documentos = lote.stream()
                .map(trecho -> paraDocumento(documento, trecho))
                .toList();
        try {
            vectorStore.add(documentos);
        } catch (RuntimeException falha) {
            throw TradutorFalhasEmbedding.traduzir(falha);
        }
    }

    private static Document paraDocumento(br.com.coti.conhecimento.dominio.Documento documento, Trecho trecho) {
        Document resultado = Document.builder()
                .id(trecho.id().toString())
                .text(trecho.texto())
                .metadata(metadados(documento, trecho))
                .build();
        resultado.setContentFormatter(SOMENTE_O_TEXTO);
        return resultado;
    }

    @Override
    public int promoverTrechos(UUID documentoId) {
        return jdbc.sql("""
                UPDATE vector_store
                   SET metadata = jsonb_set(metadata::jsonb, '{status}', to_jsonb(CAST(:novo AS text)))::json
                 WHERE metadata ->> 'documentoId' = :documentoId
                   AND metadata ->> 'status' = :atual""")
                .param("novo", StatusDocumento.PUBLICADO.name())
                .param("atual", StatusDocumento.PROCESSANDO.name())
                .param("documentoId", documentoId.toString())
                .update();
    }

    @Override
    public void removerPorDocumento(UUID documentoId) {
        vectorStore.delete(new FilterExpressionBuilder().eq(CHAVE_DOCUMENTO_ID, documentoId.toString()).build());
    }

    /** Metadados obrigatórios de cada trecho (specs/03-architecture.md §4.1, RN02.3). */
    private static Map<String, Object> metadados(Documento documento, Trecho trecho) {
        Map<String, Object> metadados = new LinkedHashMap<>();
        metadados.put(CHAVE_DOCUMENTO_ID, documento.id().toString());
        metadados.put("titulo", documento.titulo());
        metadados.put("fonte", documento.fonte());
        metadados.put("categoria", documento.categoria().name());
        metadados.put("ordem", trecho.ordem());
        metadados.put(CHAVE_STATUS, StatusDocumento.PROCESSANDO.name());
        metadados.put("modeloEmbedding", documento.modeloEmbedding());
        metadados.put("vigenteAte", VigenciaMetadado.paraMetadado(documento.vigenteAte()));
        metadados.put("cadastradoEm", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(documento.cadastradoEm()));
        return metadados;
    }
}
