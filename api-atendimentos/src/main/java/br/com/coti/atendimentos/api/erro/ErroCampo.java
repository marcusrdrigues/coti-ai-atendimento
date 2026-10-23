package br.com.coti.atendimentos.api.erro;

/** Campo inválido de uma requisição. A mensagem descreve a regra violada e nunca ecoa o valor recebido. */
public record ErroCampo(String campo, String mensagem) {
}
