package br.com.coti.conhecimento.dominio.excecao;

/** O conteúdo contém dado pessoal; o cadastro é recusado e nada é gravado (RN01.4). */
public class DadoPessoalDetectadoException extends RuntimeException {

    public DadoPessoalDetectadoException() {
        super("O conteúdo contém dados pessoais");
    }
}
