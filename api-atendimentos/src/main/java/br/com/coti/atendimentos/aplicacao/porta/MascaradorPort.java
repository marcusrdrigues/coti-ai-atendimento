package br.com.coti.atendimentos.aplicacao.porta;

/**
 * Remove dado pessoal do texto antes de qualquer envio a terceiros (api-conhecimento, OpenAI) e antes de log
 * (RF06, LGPD). É uma camada que falha, não uma garantia: a defesa principal é não registrar a pergunta.
 */
public interface MascaradorPort {

    String mascarar(String texto);
}
