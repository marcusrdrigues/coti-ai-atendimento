package br.com.coti.conhecimento.infraestrutura.config;

import br.com.coti.conhecimento.dominio.ConfiguracaoDivisao;
import br.com.coti.conhecimento.dominio.LimitesBusca;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.List;
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
        @Valid @NotNull Embedding embedding,
        @Valid @NotNull Analise analise,
        @Valid @DefaultValue Divisao divisao,
        @Valid @DefaultValue Embeddings embeddings,
        @Valid @DefaultValue Busca busca,
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
            String monitoramentoApiKey,
            @NotBlank(message = "defina a variável de ambiente CONHECIMENTO_MCP_API_KEY")
            @Pattern(regexp = SEM_PLACEHOLDER, message = "defina a variável de ambiente CONHECIMENTO_MCP_API_KEY")
            String mcpApiKey) {

        @Override
        public String toString() {
            return "Seguranca[adminApiKey=***, monitoramentoApiKey=***, mcpApiKey=***]";
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

    /** Modelo de embedding gravado em cada documento (o mesmo configurado em {@code spring.ai.openai.embedding}). */
    public record Embedding(@NotBlank String modelo) {
    }

    /** Divisão em trechos (RF02), em caracteres. A coerência entre os valores é checada ao criar a configuração. */
    public record Divisao(
            @Positive @DefaultValue("1500") int tamanhoMaximo,
            @PositiveOrZero @DefaultValue("200") int sobreposicao,
            @Positive @DefaultValue("50") int tamanhoMinimo,
            @Positive @DefaultValue("500") int maximoTrechos) {

        public ConfiguracaoDivisao paraDominio() {
            return new ConfiguracaoDivisao(tamanhoMaximo, sobreposicao, tamanhoMinimo, maximoTrechos);
        }
    }

    /** Geração de embeddings (RF03): lotes e novas tentativas só para falhas transitórias, com atraso exponencial. */
    public record Embeddings(
            @Positive @DefaultValue("100") int tamanhoLote,
            @PositiveOrZero @DefaultValue("2") int novasTentativas,
            @DefaultValue("200ms") Duration atrasoInicial,
            @Positive @DefaultValue("2") double multiplicador,
            @DefaultValue("2s") Duration atrasoMaximo,
            @DefaultValue("100ms") Duration jitter) {
    }

    /**
     * Busca semântica (RF04). O limiar 0,70 é inicial e deve ser confirmado pelo eval de recuperação (Fase 8);
     * a coerência entre os valores é checada ao criar {@link LimitesBusca}.
     */
    public record Busca(
            @Positive @DefaultValue("3") int perguntaMinima,
            @Positive @DefaultValue("1000") int perguntaMaxima,
            @Positive @DefaultValue("5") int limitePadrao,
            @Positive @DefaultValue("10") int limiteMaximo,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.70") double limiarSimilaridade,
            @Positive @DefaultValue("2000") int textoMaximoPorTrecho,
            @Positive @DefaultValue("8000") int textoMaximoTotal,
            @DefaultValue("5s") Duration tempoLimite) {

        public LimitesBusca paraDominio() {
            return new LimitesBusca(perguntaMinima, perguntaMaxima, limitePadrao, limiteMaximo, limiarSimilaridade,
                    textoMaximoPorTrecho, textoMaximoTotal, tempoLimite);
        }
    }

    /**
     * Padrões da análise de conteúdo (RF01). Os de injeção são expressões regulares; e-mails e telefones
     * institucionais podem ser liberados para não serem tratados como dado pessoal.
     */
    public record Analise(
            @NotEmpty List<@NotBlank String> padroesInjecao,
            @DefaultValue List<String> dominiosEmailPermitidos,
            @DefaultValue List<String> telefonesPermitidos) {
    }
}
