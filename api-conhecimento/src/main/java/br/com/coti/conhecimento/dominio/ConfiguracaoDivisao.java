package br.com.coti.conhecimento.dominio;

/**
 * Parâmetros da divisão em trechos (RF02), medidos em caracteres.
 *
 * @param tamanhoMaximo  tamanho máximo de um trecho (padrão 1.500)
 * @param sobreposicao   trecho de contexto repetido no início do trecho seguinte (padrão 200)
 * @param tamanhoMinimo  tamanho mínimo de um trecho, exceto o último e o documento curto (padrão 50)
 * @param maximoTrechos  acima disso o documento é recusado (padrão 500)
 */
public record ConfiguracaoDivisao(int tamanhoMaximo, int sobreposicao, int tamanhoMinimo, int maximoTrechos) {

    public ConfiguracaoDivisao {
        if (tamanhoMaximo < 1) {
            throw new IllegalArgumentException("tamanhoMaximo deve ser maior que zero");
        }
        if (sobreposicao < 0 || sobreposicao >= tamanhoMaximo) {
            throw new IllegalArgumentException("sobreposicao deve ser maior ou igual a 0 e menor que tamanhoMaximo");
        }
        if (tamanhoMinimo < 1 || tamanhoMinimo > tamanhoMaximo) {
            throw new IllegalArgumentException("tamanhoMinimo deve estar entre 1 e tamanhoMaximo");
        }
        if (maximoTrechos < 1) {
            throw new IllegalArgumentException("maximoTrechos deve ser maior que zero");
        }
    }

    public static ConfiguracaoDivisao padrao() {
        return new ConfiguracaoDivisao(1500, 200, 50, 500);
    }
}
