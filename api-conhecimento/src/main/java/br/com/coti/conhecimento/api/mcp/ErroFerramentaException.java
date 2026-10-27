package br.com.coti.conhecimento.api.mcp;

/**
 * Falha da ferramenta MCP. É uma {@link RuntimeException} de propósito: o Spring AI 2.0.1 converte exceções não
 * checadas do método {@code @McpTool} em resultado com {@code isError = true}. A mensagem é o JSON do
 * {@link ErroFerramenta}.
 *
 * <p>O Spring AI 2.0.1 monta o texto do erro como {@code mensagem + quebra de linha + mensagem da causa raiz}. Sem
 * causa, a causa raiz é a própria exceção e o JSON sairia duplicado, o que o cliente não consegue interpretar.
 * Por isso a exceção leva uma causa de mensagem vazia: o texto fica sendo o JSON seguido de uma quebra de linha
 * (verificado por {@code BuscarConhecimentoToolContratoIT}). Se uma atualização da biblioteca mudar esse formato,
 * o teste de contrato acusa.
 */
public class ErroFerramentaException extends RuntimeException {

    private final ErroFerramenta erro;

    public ErroFerramentaException(ErroFerramenta erro) {
        super(erro.paraJson(), new CausaSemMensagem());
        this.erro = erro;
    }

    public ErroFerramenta erro() {
        return erro;
    }

    /** Causa que só existe para o texto do erro não repetir o JSON; sem mensagem e sem pilha. */
    private static final class CausaSemMensagem extends RuntimeException {

        private static final long serialVersionUID = 1L;

        CausaSemMensagem() {
            super("", null, false, false);
        }
    }
}
