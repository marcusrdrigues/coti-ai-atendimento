package br.com.coti.atendimentos.infraestrutura.mcp;

import br.com.coti.atendimentos.dominio.Categoria;
import br.com.coti.atendimentos.dominio.TrechoConhecimento;
import br.com.coti.atendimentos.dominio.excecao.BaseConhecimentoIndisponivelException;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Valida o resultado da ferramenta {@code buscar_conhecimento} contra o {@code outputSchema} do contrato
 * (specs/06-api-contracts.md §4.2 e §6.2) e o converte para o domínio. O resultado é rejeitado por inteiro: nada
 * de um resultado inválido é usado parcialmente. As mensagens das exceções dizem o que está errado, nunca o
 * conteúdo recebido.
 */
public final class ValidadorResultadoBusca {

    static final int MAXIMO_TRECHOS = 10;
    static final int MAXIMO_TEXTO = 2_000;

    private static final Pattern CODIGO_DE_ERRO = Pattern.compile("^[A-Z_]{3,60}$");

    private final JsonMapper json = JsonMapper.builder().build();

    public List<TrechoConhecimento> validar(CallToolResult resultado) {
        if (resultado == null) {
            throw invalido("resultado ausente");
        }
        if (Boolean.TRUE.equals(resultado.isError())) {
            throw invalido("a ferramenta devolveu erro (codigo=" + codigoDoErro(resultado) + ")");
        }
        if (resultado.structuredContent() == null) {
            throw invalido("structuredContent ausente");
        }
        JsonNode raiz = json.valueToTree(resultado.structuredContent());
        if (!raiz.isObject()) {
            throw invalido("structuredContent não é um objeto");
        }
        JsonNode trechos = raiz.get("trechos");
        JsonNode total = raiz.get("total");
        if (trechos == null || !trechos.isArray()) {
            throw invalido("campo 'trechos' ausente ou não é uma lista");
        }
        if (total == null || !total.isIntegralNumber() || total.asInt() < 0) {
            throw invalido("campo 'total' ausente ou inválido");
        }
        if (trechos.size() > MAXIMO_TRECHOS) {
            throw invalido("mais de " + MAXIMO_TRECHOS + " trechos");
        }
        if (total.asInt() != trechos.size()) {
            throw invalido("'total' diferente da quantidade de trechos");
        }
        List<TrechoConhecimento> validos = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < trechos.size(); i++) {
            TrechoConhecimento trecho = converter(trechos.get(i), i);
            if (!ids.add(trecho.id())) {
                throw invalido("id de trecho repetido");
            }
            validos.add(trecho);
        }
        return List.copyOf(validos);
    }

    private TrechoConhecimento converter(JsonNode no, int indice) {
        if (!no.isObject()) {
            throw invalido("trechos[" + indice + "] não é um objeto");
        }
        UUID id = uuid(no, "id", indice);
        String texto = texto(no, "texto", indice);
        UUID documentoId = uuid(no, "documentoId", indice);
        String titulo = texto(no, "titulo", indice);
        String fonte = texto(no, "fonte", indice);
        if (texto.length() > MAXIMO_TEXTO) {
            throw invalido("trechos[" + indice + "].texto acima de " + MAXIMO_TEXTO + " caracteres");
        }
        return new TrechoConhecimento(id, texto, documentoId, titulo, fonte, categoria(no, indice),
                similaridade(no, indice));
    }

    private static String texto(JsonNode no, String campo, int indice) {
        JsonNode valor = no.get(campo);
        if (valor == null || !valor.isString() || valor.asString().isBlank()) {
            throw invalido("trechos[" + indice + "]." + campo + " ausente ou inválido");
        }
        return valor.asString();
    }

    private static UUID uuid(JsonNode no, String campo, int indice) {
        String valor = texto(no, campo, indice);
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw invalido("trechos[" + indice + "]." + campo + " não é um UUID");
        }
    }

    private static Categoria categoria(JsonNode no, int indice) {
        String valor = texto(no, "categoria", indice);
        try {
            return Categoria.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw invalido("trechos[" + indice + "].categoria desconhecida");
        }
    }

    private static Double similaridade(JsonNode no, int indice) {
        JsonNode valor = no.get("similaridade");
        if (valor == null || valor.isNull()) {
            return null;
        }
        if (!valor.isNumber()) {
            throw invalido("trechos[" + indice + "].similaridade não é um número");
        }
        double numero = valor.asDouble();
        if (Double.isNaN(numero) || numero < 0.0 || numero > 1.0) {
            throw invalido("trechos[" + indice + "].similaridade fora de 0 a 1");
        }
        return numero;
    }

    /** O código estável do erro da ferramenta, só para diagnóstico no log; qualquer outra coisa vira "desconhecido". */
    private String codigoDoErro(CallToolResult resultado) {
        try {
            List<Content> conteudo = resultado.content();
            if (conteudo != null && !conteudo.isEmpty() && conteudo.get(0) instanceof TextContent texto) {
                String codigo = json.readTree(texto.text().strip()).path("codigo").asString("");
                if (CODIGO_DE_ERRO.matcher(codigo).matches()) {
                    return codigo;
                }
            }
        } catch (RuntimeException e) {
            // texto de erro do SDK ou de outro formato: sem código conhecido
        }
        return "desconhecido";
    }

    private static BaseConhecimentoIndisponivelException invalido(String motivo) {
        return new BaseConhecimentoIndisponivelException("Resultado da busca recusado: " + motivo + ".");
    }
}
