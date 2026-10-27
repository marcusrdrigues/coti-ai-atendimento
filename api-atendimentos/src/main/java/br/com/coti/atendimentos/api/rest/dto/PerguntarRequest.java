package br.com.coti.atendimentos.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/atendimentos/perguntar} (contrato, seção 5.1). */
public record PerguntarRequest(

        @Schema(description = "Pergunta do aluno, de 3 a 1.000 caracteres",
                example = "Preciso saber lógica de programação para fazer o curso de Java?")
        @NotNull @Size(min = 3, max = 1000)
        String pergunta) {

    /** A pergunta pode conter dado pessoal: nunca aparece em log nem em mensagem. */
    @Override
    public String toString() {
        return "PerguntarRequest[tamanho=" + (pergunta == null ? 0 : pergunta.length()) + "]";
    }
}
