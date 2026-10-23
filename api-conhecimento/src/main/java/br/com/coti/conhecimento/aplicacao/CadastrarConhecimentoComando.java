package br.com.coti.conhecimento.aplicacao;

import br.com.coti.conhecimento.dominio.Categoria;
import java.time.LocalDate;

/** Dados de um cadastro, já validados na borda (tamanhos e formatos). */
public record CadastrarConhecimentoComando(
        String titulo,
        String conteudo,
        String fonte,
        Categoria categoria,
        LocalDate vigenteAte,
        String cadastradoPor) {
}
