package br.com.coti.atendimentos.dominio;

/**
 * Um trecho que entrou no contexto do modelo. O modelo só vê o {@code rotulo} ({@code T1}, {@code T2}, ...) e o
 * {@code textoExibido} (já sanitizado e cortado); o {@code trecho} original do resultado MCP fica em memória para
 * derivar as fontes, e o UUID nunca vai ao modelo.
 */
public record ReferenciaTrecho(String rotulo, TrechoConhecimento trecho, String textoExibido) {

    @Override
    public String toString() {
        return "ReferenciaTrecho[rotulo=" + rotulo + ", tamanho=" + textoExibido.length() + "]";
    }
}
