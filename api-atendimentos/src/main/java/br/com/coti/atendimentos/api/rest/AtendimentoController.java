package br.com.coti.atendimentos.api.rest;

import br.com.coti.atendimentos.api.erro.CorrelacaoHttp;
import br.com.coti.atendimentos.api.rest.dto.PerguntarRequest;
import br.com.coti.atendimentos.api.rest.dto.RespostaAtendimentoResponse;
import br.com.coti.atendimentos.aplicacao.ResponderPerguntaService;
import br.com.coti.atendimentos.dominio.PerguntaAluno;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Atendimento ao aluno (RF06). Público no MVP: não devolve dado individual. */
@RestController
@RequestMapping(path = "/api/atendimentos", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Atendimento", description = "Perguntas dos alunos sobre a COTI")
public class AtendimentoController {

    private final ResponderPerguntaService servico;

    public AtendimentoController(ResponderPerguntaService servico) {
        this.servico = servico;
    }

    @PostMapping(path = "/perguntar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Fazer uma pergunta",
            description = "Responde com base nos materiais oficiais da COTI, citando as fontes. Sem evidência, "
                    + "devolve 200 com conhecimentoSuficiente=false e o canal de atendimento humano.")
    public RespostaAtendimentoResponse perguntar(@Valid @RequestBody PerguntarRequest requisicao,
                                                 HttpServletRequest http) {
        var resultado = servico.responder(new PerguntaAluno(requisicao.pergunta()));
        return RespostaAtendimentoResponse.de(CorrelacaoHttp.obter(http), resultado);
    }
}
