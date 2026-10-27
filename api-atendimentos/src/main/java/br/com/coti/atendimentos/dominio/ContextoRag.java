package br.com.coti.atendimentos.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Contexto entregue ao modelo (contrato, seção 6.3): o bloco {@code <trechos>} já montado e o mapa dos rótulos
 * para os trechos do resultado MCP. Vazio quando nenhum trecho sobrou depois do filtro, da ordenação e dos
 * limites; nesse caso o modelo não é chamado.
 */
public record ContextoRag(List<ReferenciaTrecho> referencias, String bloco) {

    public ContextoRag {
        referencias = List.copyOf(referencias);
    }

    public static ContextoRag semTrechos() {
        return new ContextoRag(List.of(), "");
    }

    public boolean vazio() {
        return referencias.isEmpty();
    }

    public Optional<ReferenciaTrecho> buscar(String rotulo) {
        return referencias.stream().filter(r -> r.rotulo().equals(rotulo)).findFirst();
    }

    /** Só o tamanho: o bloco tem texto de trechos e não vai para log. */
    @Override
    public String toString() {
        return "ContextoRag[trechos=" + referencias.size() + ", tamanho=" + bloco.length() + "]";
    }
}
