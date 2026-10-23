package br.com.coti.conhecimento.infraestrutura.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Propriedades próprias da api-conhecimento (prefixo {@code coti.conhecimento}). Credenciais obrigatórias:
 * a mensagem de validação cita o nome da variável de ambiente, nunca o valor.
 */
@Validated
@ConfigurationProperties(prefix = "coti.conhecimento")
public record PropriedadesConhecimento(
        @Valid @NotNull Seguranca seguranca,
        @Valid @NotNull OpenAi openai,
        @Valid @DefaultValue Web web,
        @DefaultValue("false") boolean documentacaoPublica) {

    /**
     * Quando a variável de ambiente não existe, o Spring mantém o texto literal {@code ${NOME}} ao vincular
     * propriedades. Esse valor precisa ser recusado, senão a "chave" seria o próprio nome da variável.
     */
    private static final String SEM_PLACEHOLDER = "^(?!\\$\\{).*";

    public record Seguranca(
            @NotBlank(message = "defina a variável de ambiente CONHECIMENTO_ADMIN_API_KEY")
            @Pattern(regexp = SEM_PLACEHOLDER, message = "defina a variável de ambiente CONHECIMENTO_ADMIN_API_KEY")
            String adminApiKey,
            @NotBlank(message = "defina a variável de ambiente MONITORAMENTO_API_KEY")
            @Pattern(regexp = SEM_PLACEHOLDER, message = "defina a variável de ambiente MONITORAMENTO_API_KEY")
            String monitoramentoApiKey) {

        @Override
        public String toString() {
            return "Seguranca[adminApiKey=***, monitoramentoApiKey=***]";
        }
    }

    /** Apenas valida a presença da chave; quem a usa é o Spring AI ({@code spring.ai.openai.api-key}). */
    public record OpenAi(
            @NotBlank(message = "defina a variável de ambiente OPENAI_API_KEY")
            @Pattern(regexp = SEM_PLACEHOLDER, message = "defina a variável de ambiente OPENAI_API_KEY")
            String apiKey) {

        @Override
        public String toString() {
            return "OpenAi[apiKey=***]";
        }
    }

    public record Web(@Positive @DefaultValue("262144") long limiteCorpoBytes) {
    }
}
