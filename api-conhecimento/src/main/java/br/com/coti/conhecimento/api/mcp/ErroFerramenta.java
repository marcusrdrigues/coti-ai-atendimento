package br.com.coti.conhecimento.api.mcp;

import tools.jackson.databind.json.JsonMapper;

/**
 * Erro da ferramenta como o contrato o publica (specs/06-api-contracts.md §4.5): um código estável e uma mensagem
 * segura, sem SQL, stack trace, nome de classe nem o valor recebido.
 */
public record ErroFerramenta(String codigo, String mensagem) {

    public static final String ARGUMENTOS_INVALIDOS = "ARGUMENTOS_INVALIDOS";
    public static final String BASE_CONHECIMENTO_INDISPONIVEL = "BASE_CONHECIMENTO_INDISPONIVEL";
    public static final String MODELO_INDISPONIVEL = "MODELO_INDISPONIVEL";
    public static final String ERRO_INTERNO = "ERRO_INTERNO";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public String paraJson() {
        return JSON.writeValueAsString(this);
    }
}
