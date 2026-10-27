package br.com.coti.conhecimento.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/conhecimentos/diagnostico/busca} (contrato, seção 3.5). */
public record BuscaDiagnosticoRequest(

        @Schema(description = "Pergunta a pesquisar na base", example = "Preciso saber programar antes do curso de Java?")
        @NotNull @Size(min = 3, max = 1000)
        String pergunta,

        @Schema(description = "Quantidade máxima de trechos (padrão 5)", example = "3")
        @Min(1) @Max(10)
        Integer limite) {

    /** A pergunta pode ser dado pessoal do aluno: nunca aparece em texto de log ou de erro. */
    @Override
    public String toString() {
        return "BuscaDiagnosticoRequest[limite=" + limite + "]";
    }
}
