package br.com.coti.atendimentos.dominio.excecao;

/**
 * A saída do modelo não pôde ser lida no formato esperado (JSON fora do schema). O caso de uso trata como saída
 * reprovada: reenvia com o motivo, até o limite, e depois responde "sem evidência". Nunca é remendada.
 */
public class SaidaModeloInvalidaException extends RuntimeException {

    public SaidaModeloInvalidaException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
