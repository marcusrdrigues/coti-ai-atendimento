package br.com.coti.atendimentos.infraestrutura.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Propriedades próprias da api-atendimentos (prefixo {@code coti.atendimentos}). Variáveis obrigatórias: a
 * mensagem de validação cita o nome da variável de ambiente, nunca o valor.
 */
@Validated
@ConfigurationProperties(prefix = "coti.atendimentos")
public record PropriedadesAtendimentos(
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
            @NotBlank(message = "defina a variável de ambiente MONITORAMENTO_API_KEY")
            @Pattern(regexp = SEM_PLACEHOLDER, message = "defina a variável de ambiente MONITORAMENTO_API_KEY")
            String monitoramentoApiKey) {

        @Override
        public String toString() {
            return "Seguranca[monitoramentoApiKey=***]";
        }
    }

    /**
     * Apenas valida a presença; quem usa é o Spring AI ({@code spring.ai.openai.api-key} e
     * {@code spring.ai.openai.chat.model}). O modelo de chat não tem valor padrão: precisa de identificador
     * com versão, escolhido pelo eval (ADR-15).
     */
    public record OpenAi(
            @NotBlank(message = "defina a variável de ambiente OPENAI_API_KEY")
            @Pattern(regexp = SEM_PLACEHOLDER, message = "defina a variável de ambiente OPENAI_API_KEY")
            String apiKey,
            @NotBlank(message = "defina a variável de ambiente OPENAI_CHAT_MODEL")
            @Pattern(regexp = SEM_PLACEHOLDER, message = "defina a variável de ambiente OPENAI_CHAT_MODEL")
            String chatModel) {

        @Override
        public String toString() {
            return "OpenAi[apiKey=***, chatModel=" + chatModel + "]";
        }
    }

    public record Web(@Positive @DefaultValue("262144") long limiteCorpoBytes) {
    }
}
