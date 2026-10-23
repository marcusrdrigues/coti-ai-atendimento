package br.com.coti.conhecimento.api.rest.dto;

import br.com.coti.conhecimento.dominio.Categoria;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Corpo de {@code POST /api/conhecimentos} (contrato, seção 3.2). */
public record CadastrarConhecimentoRequest(

        @Schema(description = "Título do documento", example = "Curso Java Fullstack — pré-requisitos")
        @NotBlank @Size(min = 3, max = 200)
        @Pattern(regexp = SEM_ESPACO_NAS_PONTAS, message = "não pode começar nem terminar com espaço")
        String titulo,

        @Schema(description = "Texto puro ou Markdown", example = "## Pré-requisitos\n\nConhecimento básico de lógica...")
        @NotBlank @Size(min = 50, max = 100_000)
        String conteudo,

        @Schema(description = "Origem legível do conteúdo", example = "Site COTI — página do curso Java Fullstack, out/2026")
        @NotBlank @Size(min = 3, max = 300)
        @Pattern(regexp = SEM_ESPACO_NAS_PONTAS, message = "não pode começar nem terminar com espaço")
        String fonte,

        @NotNull Categoria categoria,

        @Schema(description = "Data até a qual o conteúdo vale (AAAA-MM-DD), para preços e turmas", example = "2027-06-30")
        @FutureOrPresent
        LocalDate vigenteAte) {

    static final String SEM_ESPACO_NAS_PONTAS = "^(?!\\s).*(?<!\\s)$";
}
