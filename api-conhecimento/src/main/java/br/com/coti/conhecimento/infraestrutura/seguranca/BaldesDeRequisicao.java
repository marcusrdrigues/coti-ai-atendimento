package br.com.coti.conhecimento.infraestrutura.seguranca;

/** Os baldes de cadastro e de diagnóstico, um bean só para o filtro e os testes enxergarem o mesmo estado. */
public record BaldesDeRequisicao(BaldeTokens cadastro, BaldeTokens diagnostico) {

    public void limpar() {
        cadastro.limpar();
        diagnostico.limpar();
    }
}
