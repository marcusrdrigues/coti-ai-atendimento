package br.com.coti.conhecimento.infraestrutura.vetorial;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingException;
import br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingTransitoriaException;
import br.com.coti.conhecimento.suporte.EmbeddingModelFalso;
import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.NotFoundException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.UnprocessableEntityException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class TradutorFalhasEmbeddingTest {

    private static final Headers CABECALHOS = Headers.builder().build();

    @Test
    void conexaoETempoEsgotadoSaoTransitorios() {
        RuntimeException original = EmbeddingModelFalso.tempoEsgotado();

        RuntimeException traduzida = TradutorFalhasEmbedding.traduzir(original);

        assertThat(traduzida).isInstanceOf(FalhaEmbeddingTransitoriaException.class).hasCause(original);
    }

    @Test
    void limiteDeRequisicoes429EhTransitorio() {
        assertThat(TradutorFalhasEmbedding.traduzir(EmbeddingModelFalso.limiteDeRequisicoes()))
                .isInstanceOf(FalhaEmbeddingTransitoriaException.class);
    }

    @Test
    void erro5xxEhTransitorio() {
        assertThat(TradutorFalhasEmbedding.traduzir(EmbeddingModelFalso.erroDoServidor()))
                .isInstanceOf(FalhaEmbeddingTransitoriaException.class);
        assertThat(TradutorFalhasEmbedding.traduzir(
                InternalServerException.builder().statusCode(503).headers(CABECALHOS).build()))
                .isInstanceOf(FalhaEmbeddingTransitoriaException.class);
    }

    @Test
    void credencialRecusadaEPermissaoNegadaSaoDefinitivas() {
        assertThat(TradutorFalhasEmbedding.traduzir(EmbeddingModelFalso.credencialRecusada()))
                .isInstanceOf(FalhaEmbeddingException.class);
        assertThat(TradutorFalhasEmbedding.traduzir(PermissionDeniedException.builder().headers(CABECALHOS).build()))
                .isInstanceOf(FalhaEmbeddingException.class);
    }

    @Test
    void requisicaoInvalidaENaoEncontradoSaoDefinitivos() {
        assertThat(TradutorFalhasEmbedding.traduzir(BadRequestException.builder().headers(CABECALHOS).build()))
                .isInstanceOf(FalhaEmbeddingException.class);
        assertThat(TradutorFalhasEmbedding.traduzir(NotFoundException.builder().headers(CABECALHOS).build()))
                .isInstanceOf(FalhaEmbeddingException.class);
        assertThat(TradutorFalhasEmbedding.traduzir(UnprocessableEntityException.builder().headers(CABECALHOS).build()))
                .isInstanceOf(FalhaEmbeddingException.class);
    }

    @Test
    void encontraAFalhaDoSdkMesmoEmbrulhadaPorOutraExcecao() {
        RuntimeException embrulhada = new IllegalStateException("erro ao gerar embeddings",
                EmbeddingModelFalso.limiteDeRequisicoes());

        assertThat(TradutorFalhasEmbedding.traduzir(embrulhada)).isInstanceOf(FalhaEmbeddingTransitoriaException.class);
    }

    @Test
    void falhaQueNaoEDoSdkPassaSemTraducao() {
        RuntimeException banco = new DataAccessResourceFailureException("conexão perdida");

        assertThat(TradutorFalhasEmbedding.traduzir(banco)).isSameAs(banco);
    }

    @Test
    void aMensagemDoSdkNaoVazaParaAExcecaoDeDominio() {
        RuntimeException traduzida = TradutorFalhasEmbedding.traduzir(EmbeddingModelFalso.credencialRecusada());

        assertThat(traduzida.getMessage()).contains("UnauthorizedException").doesNotContain("Bearer").doesNotContain("sk-");
    }
}
