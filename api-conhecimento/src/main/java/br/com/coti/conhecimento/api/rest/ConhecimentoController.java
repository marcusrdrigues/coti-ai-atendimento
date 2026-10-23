package br.com.coti.conhecimento.api.rest;

import br.com.coti.conhecimento.api.rest.dto.CadastrarConhecimentoRequest;
import br.com.coti.conhecimento.api.rest.dto.ConhecimentoCadastradoResponse;
import br.com.coti.conhecimento.api.rest.dto.ConhecimentoDetalheResponse;
import br.com.coti.conhecimento.api.rest.dto.PaginaConhecimentosResponse;
import br.com.coti.conhecimento.aplicacao.CadastrarConhecimentoComando;
import br.com.coti.conhecimento.aplicacao.CadastrarConhecimentoService;
import br.com.coti.conhecimento.aplicacao.ConsultarConhecimentosService;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.FiltroDocumentos;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cadastro e consulta de conhecimentos pelo gestor de conteúdo (RF01 e RF11). Exige a chave administrativa. */
@RestController
@RequestMapping(path = "/api/conhecimentos", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Conhecimentos", description = "Base de conhecimento oficial da COTI (uso administrativo)")
public class ConhecimentoController {

    private final CadastrarConhecimentoService cadastrar;
    private final ConsultarConhecimentosService consultar;

    public ConhecimentoController(CadastrarConhecimentoService cadastrar, ConsultarConhecimentosService consultar) {
        this.cadastrar = cadastrar;
        this.consultar = consultar;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cadastrar conhecimento",
            description = "201 quando publicado; 202 quando retido para revisão por possível prompt injection.")
    public ResponseEntity<ConhecimentoCadastradoResponse> cadastrar(
            @Valid @RequestBody CadastrarConhecimentoRequest requisicao, Authentication autenticacao) {
        Documento documento = cadastrar.cadastrar(new CadastrarConhecimentoComando(
                requisicao.titulo(), requisicao.conteudo(), requisicao.fonte(), requisicao.categoria(),
                requisicao.vigenteAte(), autenticacao.getName()));
        ConhecimentoCadastradoResponse resposta = ConhecimentoCadastradoResponse.de(documento);
        if (documento.status() == StatusDocumento.EM_REVISAO) {
            return ResponseEntity.accepted().body(resposta);
        }
        return ResponseEntity.created(URI.create("/api/conhecimentos/" + documento.id())).body(resposta);
    }

    @GetMapping
    @Operation(summary = "Listar conhecimentos", description = "Mais recentes primeiro; não traz o conteúdo.")
    public PaginaConhecimentosResponse listar(
            @RequestParam(required = false) StatusDocumento status,
            @RequestParam(required = false) Categoria categoria,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return PaginaConhecimentosResponse.de(
                consultar.listar(new FiltroDocumentos(status, categoria), pagina, tamanho));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhar conhecimento", description = "Inclui o conteúdo cadastrado.")
    public ConhecimentoDetalheResponse detalhar(@PathVariable UUID id) {
        return ConhecimentoDetalheResponse.de(consultar.detalhar(id));
    }
}
