package br.com.coti.conhecimento.api.mcp;

import br.com.coti.conhecimento.aplicacao.BuscarConhecimentoService;
import br.com.coti.conhecimento.dominio.excecao.ArgumentosInvalidosException;
import br.com.coti.conhecimento.dominio.excecao.BaseConhecimentoIndisponivelException;
import br.com.coti.conhecimento.dominio.excecao.ModeloIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Ferramenta MCP {@code buscar_conhecimento} (RF05), a única porta de busca da {@code api-atendimentos}. Só
 * traduz o protocolo: valida e busca pelo caso de uso {@link BuscarConhecimentoService}, o mesmo do diagnóstico.
 * Somente leitura. Os erros saem como {@link ErroFerramentaException} com mensagem segura; o detalhe técnico vai
 * só para o log. A pergunta nunca é registrada.
 */
@Component
public class BuscarConhecimentoTool {

    private static final Logger log = LoggerFactory.getLogger(BuscarConhecimentoTool.class);

    private final BuscarConhecimentoService busca;

    public BuscarConhecimentoTool(BuscarConhecimentoService busca) {
        this.busca = busca;
    }

    @McpTool(name = "buscar_conhecimento",
            title = "Buscar conhecimento COTI",
            description = "Busca trechos publicados da base de conhecimento oficial da COTI Informática "
                    + "semanticamente relacionados à pergunta.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public ResultadoBusca buscar(
            @McpToolParam(description = "Pergunta ou texto a pesquisar na base de conhecimento.", required = true)
            String pergunta,
            @McpToolParam(description = "Quantidade máxima de trechos a devolver.", required = false)
            Integer limite) {
        try {
            return ResultadoBusca.de(busca.buscar(pergunta, limite));
        } catch (ArgumentosInvalidosException falha) {
            throw erro(ErroFerramenta.ARGUMENTOS_INVALIDOS, falha.getMessage(), null);
        } catch (BaseConhecimentoIndisponivelException falha) {
            throw erro(ErroFerramenta.BASE_CONHECIMENTO_INDISPONIVEL,
                    "A base de conhecimento está temporariamente indisponível.", falha);
        } catch (ModeloIndisponivelException falha) {
            throw erro(ErroFerramenta.MODELO_INDISPONIVEL,
                    "O serviço de inteligência artificial está temporariamente indisponível.", falha);
        } catch (RuntimeException falha) {
            throw erro(ErroFerramenta.ERRO_INTERNO, "Ocorreu um erro inesperado ao buscar conhecimento.", falha);
        }
    }

    private static ErroFerramentaException erro(String codigo, String mensagem, RuntimeException causa) {
        if (causa == null) {
            log.warn("Ferramenta buscar_conhecimento recusou os argumentos. codigo={}", codigo);
        } else {
            log.error("Ferramenta buscar_conhecimento falhou. codigo={}", codigo, causa);
        }
        return new ErroFerramentaException(new ErroFerramenta(codigo, mensagem));
    }
}
