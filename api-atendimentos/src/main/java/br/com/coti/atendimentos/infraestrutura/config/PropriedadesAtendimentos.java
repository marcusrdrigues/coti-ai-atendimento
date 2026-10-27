package br.com.coti.atendimentos.infraestrutura.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
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
        @Valid @NotNull Conhecimento conhecimento,
        @Valid @NotNull Mensagens mensagens,
        @Valid @DefaultValue Web web,
        @Valid @DefaultValue Busca busca,
        @Valid @DefaultValue Resiliencia resiliencia,
        @DefaultValue("false") boolean documentacaoPublica) {

    /**
     * Quando a variável de ambiente não existe, o Spring mantém o texto literal {@code ${NOME}} ao vincular
     * propriedades. Esse valor precisa ser recusado, senão a "chave" seria o próprio nome da variável.
     */
    private static final String SEM_PLACEHOLDER = "^(?!\\$\\{).*";

    public record Seguranca(
            @NotBlank(message = "defina a variável de ambiente MONITORAMENTO_API_KEY")
            @Pattern(regexp = SEM_PLACEHOLDER, message = "defina a variável de ambiente MONITORAMENTO_API_KEY")
            String monitoramentoApiKey,
            @NotBlank(message = "defina a variável de ambiente CONHECIMENTO_MCP_API_KEY")
            @Pattern(regexp = SEM_PLACEHOLDER, message = "defina a variável de ambiente CONHECIMENTO_MCP_API_KEY")
            String mcpApiKey) {

        @Override
        public String toString() {
            return "Seguranca[monitoramentoApiKey=***, mcpApiKey=***]";
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

    /** Apenas valida a presença do endereço do servidor MCP; quem o usa é o cliente MCP do Spring AI. */
    public record Conhecimento(
            @NotBlank(message = "defina a variável de ambiente CONHECIMENTO_MCP_URL")
            @Pattern(regexp = SEM_PLACEHOLDER, message = "defina a variável de ambiente CONHECIMENTO_MCP_URL")
            String url) {
    }

    /**
     * Texto devolvido quando não há evidência (RF10). A resposta é a mensagem seguida do canal humano; os dois vêm
     * de configuração, nunca do modelo (RN10.2).
     */
    public record Mensagens(
            @NotBlank(message = "defina a variável de ambiente ATENDIMENTO_MENSAGEM_SEM_EVIDENCIA")
            @Pattern(regexp = SEM_PLACEHOLDER, message = "defina a variável de ambiente ATENDIMENTO_MENSAGEM_SEM_EVIDENCIA")
            String semEvidencia,
            @NotBlank(message = "defina a variável de ambiente ATENDIMENTO_CANAL_HUMANO")
            @Pattern(regexp = SEM_PLACEHOLDER, message = "defina a variável de ambiente ATENDIMENTO_CANAL_HUMANO")
            String canalHumano) {
    }

    public record Web(@Positive @DefaultValue("262144") long limiteCorpoBytes) {
    }

    /** Busca na base (RF07). O limiar repete o do servidor, como defesa em profundidade (contrato, seção 6.3). */
    public record Busca(
            @Positive @DefaultValue("5") int limite,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.70") double limiarSimilaridade) {
    }

    /** Tempos e novas tentativas (RNF04, ADR-13). O backoff é exponencial com variação aleatória. */
    public record Resiliencia(
            @DefaultValue("5s") Duration mcpTempoLimite,
            @PositiveOrZero @DefaultValue("2") int mcpNovasTentativas,
            @DefaultValue("30s") Duration prazoTotal,
            @DefaultValue("30s") Duration retryAfter,
            @DefaultValue("200ms") Duration atrasoInicial,
            @Positive @DefaultValue("2") double multiplicador,
            @DefaultValue("2s") Duration atrasoMaximo,
            @DefaultValue("100ms") Duration jitter,
            @DefaultValue("2s") Duration saudeTempoLimite,
            @DefaultValue("10s") Duration saudeValidade) {
    }
}
