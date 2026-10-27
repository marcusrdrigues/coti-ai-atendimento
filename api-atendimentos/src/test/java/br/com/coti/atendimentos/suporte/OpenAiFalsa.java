package br.com.coti.atendimentos.suporte;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Servidor HTTP que imita só o endpoint de embeddings da OpenAI ({@code POST /v1/embeddings}), para os testes
 * entre os dois serviços reais: a {@code api-conhecimento} aponta para ele em vez de chamar a OpenAI. O vetor de
 * 1.536 dimensões depende só do texto, então o mesmo texto cai no mesmo ponto. Guarda os textos recebidos, o que
 * prova o que de fato sairia para o provedor (por exemplo, que a pergunta chegou mascarada).
 */
public final class OpenAiFalsa implements AutoCloseable {

    private static final int DIMENSOES = 1536;

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpServer servidor;
    private final List<String> textos = Collections.synchronizedList(new ArrayList<>());

    private OpenAiFalsa() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        servidor.createContext("/", this::atender);
    }

    public static OpenAiFalsa iniciar() {
        try {
            OpenAiFalsa falsa = new OpenAiFalsa();
            falsa.servidor.start();
            return falsa;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível iniciar a OpenAI falsa.", e);
        }
    }

    /** URL base no formato do SDK da OpenAI (termina em {@code /v1}). */
    public String urlBase() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1";
    }

    /** Todos os textos enviados para gerar embedding, na ordem. */
    public List<String> textosRecebidos() {
        synchronized (textos) {
            return List.copyOf(textos);
        }
    }

    public void limpar() {
        textos.clear();
    }

    private void atender(HttpExchange troca) throws IOException {
        try (troca) {
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!"POST".equals(troca.getRequestMethod()) || !troca.getRequestURI().getPath().endsWith("/embeddings")) {
                enviar(troca, 404, "{\"error\":{\"message\":\"nao suportado\"}}");
                return;
            }
            JsonNode entrada = json.readTree(corpo).get("input");
            List<String> recebidos = new ArrayList<>();
            if (entrada.isArray()) {
                entrada.forEach(n -> recebidos.add(n.asString()));
            } else {
                recebidos.add(entrada.asString());
            }
            textos.addAll(recebidos);

            ObjectNode resposta = json.createObjectNode();
            resposta.put("object", "list");
            resposta.put("model", "text-embedding-3-small");
            ArrayNode dados = resposta.putArray("data");
            for (int i = 0; i < recebidos.size(); i++) {
                ObjectNode item = dados.addObject();
                item.put("object", "embedding");
                item.put("index", i);
                ArrayNode vetor = item.putArray("embedding");
                for (float valor : vetorDe(recebidos.get(i))) {
                    vetor.add(valor);
                }
            }
            ObjectNode uso = resposta.putObject("usage");
            uso.put("prompt_tokens", recebidos.size());
            uso.put("total_tokens", recebidos.size());
            enviar(troca, 200, json.writeValueAsString(resposta));
        }
    }

    private void enviar(HttpExchange troca, int status, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", "application/json");
        troca.sendResponseHeaders(status, bytes.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }

    /** Vetor pseudoaleatório fixo para o texto, normalizado para norma 1. */
    static float[] vetorDe(String texto) {
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

    @Override
    public void close() {
        servidor.stop(0);
    }
}
