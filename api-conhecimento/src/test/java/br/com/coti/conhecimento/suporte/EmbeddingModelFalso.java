package br.com.coti.conhecimento.suporte;

import com.openai.core.http.Headers;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Dublê determinístico do modelo de embedding: o vetor de 1.536 dimensões (norma 1) depende só do texto, então o
 * mesmo texto sempre cai no mesmo ponto. Nenhuma chamada sai da JVM. Permite simular lentidão e falhas do SDK da
 * OpenAI (HTTP 429, 500, 401 e erro de conexão), inclusive "falhar só na N-ésima chamada".
 */
public final class EmbeddingModelFalso implements EmbeddingModel {

    public static final int DIMENSOES = 1536;

    private final List<List<String>> requisicoes = Collections.synchronizedList(new ArrayList<>());
    private volatile Duration atraso = Duration.ZERO;
    private volatile Supplier<RuntimeException> falha;
    private volatile int falhasRestantes;
    private volatile int falharNaChamada;
    private volatile int chamadas;

    /** Volta ao estado inicial: sem atraso, sem falha, sem histórico. */
    public synchronized void reiniciar() {
        requisicoes.clear();
        atraso = Duration.ZERO;
        falha = null;
        falhasRestantes = 0;
        falharNaChamada = 0;
        chamadas = 0;
    }

    public synchronized void atrasar(Duration duracao) {
        this.atraso = duracao;
    }

    /** As próximas {@code vezes} chamadas falham e, depois, voltam ao normal. */
    public synchronized void falharAsProximas(int vezes, Supplier<RuntimeException> excecao) {
        this.falhasRestantes = vezes;
        this.falha = excecao;
    }

    /** Falha todas as chamadas a partir de agora. */
    public synchronized void falharSempre(Supplier<RuntimeException> excecao) {
        this.falhasRestantes = Integer.MAX_VALUE;
        this.falha = excecao;
    }

    /** Falha somente a chamada de número {@code numero} (começa em 1), contando a partir de agora. */
    public synchronized void falharSomenteNaChamada(int numero, Supplier<RuntimeException> excecao) {
        this.chamadas = 0;
        this.falharNaChamada = numero;
        this.falha = excecao;
    }

    public int chamadas() {
        return requisicoes.size();
    }

    /** Textos de cada chamada, na ordem; mostra exatamente o que seria enviado ao provedor. */
    public List<List<String>> requisicoes() {
        synchronized (requisicoes) {
            return List.copyOf(requisicoes);
        }
    }

    public List<String> todosOsTextos() {
        return requisicoes().stream().flatMap(List::stream).toList();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest requisicao) {
        List<String> textos = List.copyOf(requisicao.getInstructions());
        requisicoes.add(textos);
        dormir();
        verificarFalha();
        List<Embedding> embeddings = new ArrayList<>();
        for (int i = 0; i < textos.size(); i++) {
            embeddings.add(new Embedding(vetorDe(textos.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document documento) {
        return vetorDe(documento.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSOES;
    }

    private synchronized void verificarFalha() {
        chamadas++;
        if (falharNaChamada > 0 && chamadas == falharNaChamada) {
            throw falha.get();
        }
        if (falharNaChamada == 0 && falhasRestantes > 0 && falha != null) {
            if (falhasRestantes != Integer.MAX_VALUE) {
                falhasRestantes--;
            }
            throw falha.get();
        }
    }

    private void dormir() {
        Duration espera = atraso;
        if (!espera.isZero()) {
            try {
                Thread.sleep(espera);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Vetor pseudoaleatório fixo para o texto, normalizado para norma 1 (como os modelos reais de cosseno). */
    public static float[] vetorDe(String texto) {
        Random aleatorio = new Random(texto.hashCode());
        float[] vetor = new float[DIMENSOES];
        double soma = 0;
        for (int i = 0; i < DIMENSOES; i++) {
            vetor[i] = (float) aleatorio.nextGaussian();
            soma += vetor[i] * vetor[i];
        }
        float norma = (float) Math.sqrt(soma);
        for (int i = 0; i < DIMENSOES; i++) {
            vetor[i] /= norma;
        }
        return vetor;
    }

    // ---- falhas do SDK da OpenAI, como o Spring AI 2.0 as propaga

    public static RuntimeException limiteDeRequisicoes() {
        return RateLimitException.builder().headers(Headers.builder().build()).build();
    }

    public static RuntimeException erroDoServidor() {
        return InternalServerException.builder().statusCode(500).headers(Headers.builder().build()).build();
    }

    public static RuntimeException credencialRecusada() {
        return UnauthorizedException.builder().headers(Headers.builder().build()).build();
    }

    public static RuntimeException tempoEsgotado() {
        return new OpenAIIoException("tempo esgotado");
    }
}
