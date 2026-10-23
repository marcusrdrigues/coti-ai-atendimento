package br.com.coti.conhecimento.dominio.excecao;

import java.util.List;

/** Regra de entrada violada que só o caso de uso consegue verificar (por exemplo, tamanho após normalizar). */
public class DadosInvalidosException extends RuntimeException {

    /** Campo inválido e a regra violada, sem o valor recebido. */
    public record CampoInvalido(String campo, String mensagem) {
    }

    private final List<CampoInvalido> campos;

    public DadosInvalidosException(String campo, String mensagem) {
        this(List.of(new CampoInvalido(campo, mensagem)));
    }

    public DadosInvalidosException(List<CampoInvalido> campos) {
        super("Dados inválidos");
        this.campos = List.copyOf(campos);
    }

    public List<CampoInvalido> campos() {
        return campos;
    }
}
