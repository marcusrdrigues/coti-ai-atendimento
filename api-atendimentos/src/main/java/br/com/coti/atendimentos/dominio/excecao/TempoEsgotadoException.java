package br.com.coti.atendimentos.dominio.excecao;

/** O orçamento de tempo do atendimento (prazo total) acabou antes de a resposta ficar pronta. */
public class TempoEsgotadoException extends RuntimeException {

    public TempoEsgotadoException() {
        super("O tempo total do atendimento foi excedido.");
    }
}
