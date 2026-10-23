package br.com.coti.conhecimento.aplicacao.porta;

import br.com.coti.conhecimento.dominio.ResultadoAnalise;

/** Procura dado pessoal e sinais de prompt injection no texto normalizado de um documento. */
public interface AnalisadorConteudoPort {

    ResultadoAnalise analisar(String textoNormalizado);
}
