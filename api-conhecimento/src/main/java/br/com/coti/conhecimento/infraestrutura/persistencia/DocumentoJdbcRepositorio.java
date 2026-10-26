package br.com.coti.conhecimento.infraestrutura.persistencia;

import br.com.coti.conhecimento.aplicacao.porta.RepositorioDocumentoPort;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.DocumentoResumo;
import br.com.coti.conhecimento.dominio.FiltroDocumentos;
import br.com.coti.conhecimento.dominio.Pagina;
import br.com.coti.conhecimento.dominio.Pendencia;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import br.com.coti.conhecimento.dominio.excecao.ConhecimentoDuplicadoException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Persistência dos documentos com {@link JdbcClient}. Todo SQL usa parâmetros vinculados; os únicos trechos
 * montados dinamicamente são fragmentos constantes de {@code WHERE}. As pendências trafegam como texto
 * separado por vírgula ({@code string_to_array}/{@code array_to_string}) para evitar o mapeamento de arrays JDBC.
 */
@Component
public final class DocumentoJdbcRepositorio implements RepositorioDocumentoPort {

    private static final String COLUNAS = """
            id, titulo, conteudo, fonte, categoria, status, array_to_string(pendencias, ',') AS pendencias_csv,
            hash_conteudo, quantidade_chunks, modelo_embedding, vigente_ate, cadastrado_por, cadastrado_em""";

    private static final String COLUNAS_RESUMO = """
            id, titulo, fonte, categoria, status, quantidade_chunks, vigente_ate, cadastrado_em""";

    private static final RowMapper<Documento> MAPEADOR_DOCUMENTO = (rs, linha) -> new Documento(
            rs.getObject("id", UUID.class),
            rs.getString("titulo"),
            rs.getString("conteudo"),
            rs.getString("fonte"),
            Categoria.valueOf(rs.getString("categoria")),
            StatusDocumento.valueOf(rs.getString("status")),
            pendenciasDe(rs),
            rs.getString("hash_conteudo"),
            rs.getInt("quantidade_chunks"),
            rs.getString("modelo_embedding"),
            rs.getObject("vigente_ate", LocalDate.class),
            rs.getString("cadastrado_por"),
            rs.getObject("cadastrado_em", OffsetDateTime.class));

    private static final RowMapper<DocumentoResumo> MAPEADOR_RESUMO = (rs, linha) -> new DocumentoResumo(
            rs.getObject("id", UUID.class),
            rs.getString("titulo"),
            rs.getString("fonte"),
            Categoria.valueOf(rs.getString("categoria")),
            StatusDocumento.valueOf(rs.getString("status")),
            rs.getInt("quantidade_chunks"),
            rs.getObject("vigente_ate", LocalDate.class),
            rs.getObject("cadastrado_em", OffsetDateTime.class));

    private final JdbcClient jdbc;

    public DocumentoJdbcRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void inserir(Documento documento) {
        try {
            jdbc.sql("""
                    INSERT INTO documento (id, titulo, conteudo, fonte, categoria, status, pendencias,
                        hash_conteudo, quantidade_chunks, modelo_embedding, vigente_ate, cadastrado_por, cadastrado_em)
                    VALUES (:id, :titulo, :conteudo, :fonte, :categoria, :status,
                        string_to_array(:pendencias, ','), :hash, :quantidade, :modelo,
                        CAST(:vigenteAte AS date), :cadastradoPor, :cadastradoEm)""")
                    .param("id", documento.id())
                    .param("titulo", documento.titulo())
                    .param("conteudo", documento.conteudo())
                    .param("fonte", documento.fonte())
                    .param("categoria", documento.categoria().name())
                    .param("status", documento.status().name())
                    .param("pendencias", pendenciasComoTexto(documento))
                    .param("hash", documento.hashConteudo())
                    .param("quantidade", documento.quantidadeChunks())
                    .param("modelo", documento.modeloEmbedding())
                    .param("vigenteAte", documento.vigenteAte())
                    .param("cadastradoPor", documento.cadastradoPor())
                    .param("cadastradoEm", documento.cadastradoEm())
                    .update();
        } catch (DuplicateKeyException duplicado) {
            throw new ConhecimentoDuplicadoException();
        }
    }

    @Override
    public Optional<Documento> buscarPorHash(String hashConteudo) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM documento WHERE hash_conteudo = :hash")
                .param("hash", hashConteudo)
                .query(MAPEADOR_DOCUMENTO)
                .optional();
    }

    @Override
    public Optional<Documento> buscarPorId(UUID id) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM documento WHERE id = :id")
                .param("id", id)
                .query(MAPEADOR_DOCUMENTO)
                .optional();
    }

    @Override
    public void atualizarEstado(Documento documento) {
        int linhas = jdbc.sql("""
                UPDATE documento
                   SET status = :status, quantidade_chunks = :quantidade, pendencias = string_to_array(:pendencias, ',')
                 WHERE id = :id""")
                .param("status", documento.status().name())
                .param("quantidade", documento.quantidadeChunks())
                .param("pendencias", pendenciasComoTexto(documento))
                .param("id", documento.id())
                .update();
        if (linhas != 1) {
            throw new IllegalStateException("documento a atualizar não existe: " + documento.id());
        }
    }

    @Override
    public List<Documento> buscarProcessandoAnterioresA(OffsetDateTime limite) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM documento WHERE status = 'PROCESSANDO' "
                + "AND cadastrado_em < :limite ORDER BY cadastrado_em")
                .param("limite", limite)
                .query(MAPEADOR_DOCUMENTO)
                .list();
    }

    @Override
    public Pagina<DocumentoResumo> listar(FiltroDocumentos filtro, int pagina, int tamanho) {
        StringBuilder where = new StringBuilder(" WHERE status <> 'PROCESSANDO'");
        if (filtro.status() != null) {
            where.append(" AND status = :status");
        }
        if (filtro.categoria() != null) {
            where.append(" AND categoria = :categoria");
        }

        JdbcClient.StatementSpec contagem = jdbc.sql("SELECT count(*) FROM documento" + where);
        JdbcClient.StatementSpec itens = jdbc.sql("SELECT " + COLUNAS_RESUMO + " FROM documento" + where
                + " ORDER BY cadastrado_em DESC, id LIMIT :limite OFFSET :deslocamento");
        if (filtro.status() != null) {
            contagem = contagem.param("status", filtro.status().name());
            itens = itens.param("status", filtro.status().name());
        }
        if (filtro.categoria() != null) {
            contagem = contagem.param("categoria", filtro.categoria().name());
            itens = itens.param("categoria", filtro.categoria().name());
        }

        long total = contagem.query(Long.class).single();
        List<DocumentoResumo> pagina_ = itens
                .param("limite", tamanho)
                .param("deslocamento", (long) pagina * tamanho)
                .query(MAPEADOR_RESUMO)
                .list();
        return new Pagina<>(pagina_, pagina, tamanho, total);
    }

    private static String pendenciasComoTexto(Documento documento) {
        return documento.pendencias().stream().map(Pendencia::name).collect(Collectors.joining(","));
    }

    private static List<Pendencia> pendenciasDe(ResultSet rs) throws SQLException {
        String csv = rs.getString("pendencias_csv");
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(Pendencia::valueOf).toList();
    }
}
