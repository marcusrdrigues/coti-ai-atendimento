package br.com.coti.conhecimento.infraestrutura.vetorial;

import br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingException;
import br.com.coti.conhecimento.dominio.excecao.FalhaEmbeddingTransitoriaException;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classifica as falhas do SDK da OpenAI (usado pelo Spring AI 2.0) em transitórias, que valem nova tentativa
 * (conexão, tempo esgotado, HTTP 408, 429 e 5xx), e definitivas (400, 401, 403, 404, 422 etc.). Falhas que não
 * vêm do SDK, como as do banco, passam sem tradução. A mensagem do SDK não é copiada para as exceções de
 * domínio; a causa original fica anexada só para o log interno.
 */
final class TradutorFalhasEmbedding {

    private static final Logger log = LoggerFactory.getLogger(TradutorFalhasEmbedding.class);

    private TradutorFalhasEmbedding() {
    }

    static RuntimeException traduzir(RuntimeException falha) {
        OpenAIException sdk = procurarSdk(falha);
        if (sdk == null) {
            return falha;
        }
        if (transitoria(sdk)) {
            return new FalhaEmbeddingTransitoriaException(
                    "Serviço de embeddings indisponível no momento (" + sdk.getClass().getSimpleName() + ")", sdk);
        }
        if (sdk instanceof OpenAIServiceException servico
                && (servico.statusCode() == 401 || servico.statusCode() == 403)) {
            // Erro de configuração, não da requisição: precisa de alguém olhando. Nunca registra a chave.
            log.error("A OpenAI recusou a credencial (HTTP {}). Verifique OPENAI_API_KEY e as permissões da conta.",
                    servico.statusCode());
        }
        return new FalhaEmbeddingException(
                "Serviço de embeddings recusou a requisição (" + sdk.getClass().getSimpleName() + ")", sdk);
    }

    private static boolean transitoria(OpenAIException sdk) {
        if (sdk instanceof OpenAIIoException || sdk instanceof OpenAIRetryableException) {
            return true;
        }
        if (sdk instanceof OpenAIServiceException servico) {
            int status = servico.statusCode();
            return status == 408 || status == 429 || status >= 500;
        }
        return false;
    }

    private static OpenAIException procurarSdk(Throwable falha) {
        for (Throwable atual = falha; atual != null; atual = atual.getCause() == atual ? null : atual.getCause()) {
            if (atual instanceof OpenAIException sdk) {
                return sdk;
            }
        }
        return null;
    }
}
