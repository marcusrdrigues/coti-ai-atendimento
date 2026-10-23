package br.com.coti.conhecimento.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Documento textual oficial cadastrado pelo gestor. Imutável: mudanças de estado devolvem uma nova instância
 * e só as transições previstas em {@link StatusDocumento} são aceitas.
 *
 * <p>Invariantes (as mesmas garantidas por CHECK no banco): só documento {@code PUBLICADO} tem trechos;
 * só documento {@code EM_REVISAO} tem pendências.
 */
public record Documento(
        UUID id,
        String titulo,
        String conteudo,
        String fonte,
        Categoria categoria,
        StatusDocumento status,
        List<Pendencia> pendencias,
        String hashConteudo,
        int quantidadeChunks,
        String modeloEmbedding,
        LocalDate vigenteAte,
        String cadastradoPor,
        OffsetDateTime cadastradoEm) {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    public Documento {
        Objects.requireNonNull(id, "id");
        titulo = exigirTexto(titulo, "titulo");
        conteudo = exigirTexto(conteudo, "conteudo");
        fonte = exigirTexto(fonte, "fonte");
        Objects.requireNonNull(categoria, "categoria");
        Objects.requireNonNull(status, "status");
        pendencias = pendencias == null ? List.of() : List.copyOf(pendencias);
        if (hashConteudo == null || !SHA256_HEX.matcher(hashConteudo).matches()) {
            throw new IllegalArgumentException("hashConteudo deve ser um SHA-256 em hexadecimal minúsculo");
        }
        if (quantidadeChunks < 0) {
            throw new IllegalArgumentException("quantidadeChunks não pode ser negativa");
        }
        modeloEmbedding = exigirTexto(modeloEmbedding, "modeloEmbedding");
        cadastradoPor = exigirTexto(cadastradoPor, "cadastradoPor");
        Objects.requireNonNull(cadastradoEm, "cadastradoEm");

        if ((status == StatusDocumento.PUBLICADO) != (quantidadeChunks > 0)) {
            throw new IllegalArgumentException("somente documento PUBLICADO tem trechos");
        }
        if ((status == StatusDocumento.EM_REVISAO) != !pendencias.isEmpty()) {
            throw new IllegalArgumentException("somente documento EM_REVISAO tem pendências");
        }
    }

    /** Documento aceito pela análise, ainda sem trechos: segue para divisão e embeddings. */
    public static Documento emProcessamento(UUID id, String titulo, String conteudo, String fonte,
            Categoria categoria, String hashConteudo, String modeloEmbedding, LocalDate vigenteAte,
            String cadastradoPor, OffsetDateTime cadastradoEm) {
        return new Documento(id, titulo, conteudo, fonte, categoria, StatusDocumento.PROCESSANDO, List.of(),
                hashConteudo, 0, modeloEmbedding, vigenteAte, cadastradoPor, cadastradoEm);
    }

    /** Documento retido para revisão: guardado, mas sem trechos nem embeddings. */
    public static Documento emRevisao(UUID id, String titulo, String conteudo, String fonte,
            Categoria categoria, String hashConteudo, String modeloEmbedding, LocalDate vigenteAte,
            String cadastradoPor, OffsetDateTime cadastradoEm, List<Pendencia> pendencias) {
        return new Documento(id, titulo, conteudo, fonte, categoria, StatusDocumento.EM_REVISAO, pendencias,
                hashConteudo, 0, modeloEmbedding, vigenteAte, cadastradoPor, cadastradoEm);
    }

    /** Conclui o processamento com a quantidade de trechos gravados (maior que zero). */
    public Documento publicar(int trechosGravados) {
        exigirTransicao(StatusDocumento.PUBLICADO);
        return comEstado(StatusDocumento.PUBLICADO, trechosGravados);
    }

    /** Registra a falha de processamento; nenhum trecho permanece gravado. */
    public Documento falharProcessamento() {
        exigirTransicao(StatusDocumento.ERRO_PROCESSAMENTO);
        return comEstado(StatusDocumento.ERRO_PROCESSAMENTO, 0);
    }

    public DocumentoResumo resumo() {
        return new DocumentoResumo(id, titulo, fonte, categoria, status, quantidadeChunks, vigenteAte,
                cadastradoEm);
    }

    private Documento comEstado(StatusDocumento novoStatus, int chunks) {
        return new Documento(id, titulo, conteudo, fonte, categoria, novoStatus, pendencias, hashConteudo,
                chunks, modeloEmbedding, vigenteAte, cadastradoPor, cadastradoEm);
    }

    private void exigirTransicao(StatusDocumento destino) {
        if (!status.podeIrPara(destino)) {
            throw new IllegalStateException("transição não permitida: " + status + " -> " + destino);
        }
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        return valor;
    }
}
