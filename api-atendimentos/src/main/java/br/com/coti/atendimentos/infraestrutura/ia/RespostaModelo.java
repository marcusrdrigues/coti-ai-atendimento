package br.com.coti.atendimentos.infraestrutura.ia;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Formato da saída estruturada do modelo (contrato, seção 6.4). É interno à api-atendimentos e gera o JSON Schema
 * estrito que a OpenAI valida. Todos os campos são obrigatórios; o modelo não devolve título, fonte nem ids: as
 * fontes vêm do resultado da ferramenta MCP.
 */
public record RespostaModelo(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Texto da resposta ao aluno, em português. Sem os rótulos T1, T2...")
        String resposta,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Verdadeiro somente se os trechos fornecidos respondem à pergunta.")
        boolean conhecimentoSuficiente,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Rótulos (T1, T2...) dos trechos usados. Vazio se conhecimentoSuficiente for falso.")
        List<String> referencias) {
}
