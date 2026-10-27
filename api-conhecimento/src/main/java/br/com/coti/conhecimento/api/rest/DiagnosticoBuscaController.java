package br.com.coti.conhecimento.api.rest;

import br.com.coti.conhecimento.api.rest.dto.BuscaDiagnosticoRequest;
import br.com.coti.conhecimento.api.rest.dto.BuscaDiagnosticoResponse;
import br.com.coti.conhecimento.aplicacao.BuscarConhecimentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Busca semântica de diagnóstico (RF12): o gestor vê o que a busca devolve para uma pergunta. Executa o mesmo caso
 * de uso da ferramenta MCP, com os mesmos filtros e limiar. É POST para a pergunta não ir para URL nem para o log
 * de acesso. Exige a chave administrativa e não é usado pela {@code api-atendimentos}.
 */
@RestController
@RequestMapping(path = "/api/conhecimentos/diagnostico/busca", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Diagnóstico", description = "Verificação da busca semântica (uso administrativo)")
public class DiagnosticoBuscaController {

    private final BuscarConhecimentoService busca;

    public DiagnosticoBuscaController(BuscarConhecimentoService busca) {
        this.busca = busca;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Buscar conhecimento (diagnóstico)",
            description = "Só trechos publicados, vigentes e do modelo de embedding atual, acima do limiar de similaridade.")
    public BuscaDiagnosticoResponse buscar(@Valid @RequestBody BuscaDiagnosticoRequest requisicao) {
        return BuscaDiagnosticoResponse.de(busca.buscar(requisicao.pergunta(), requisicao.limite()));
    }
}
