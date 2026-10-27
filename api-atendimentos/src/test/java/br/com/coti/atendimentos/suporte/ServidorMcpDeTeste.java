package br.com.coti.atendimentos.suporte;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Servidor MCP mínimo para os testes da {@code api-atendimentos}: Streamable HTTP em JSON puro (sem SSE), que
 * publica a ferramenta {@code buscar_conhecimento} exatamente como o contrato da seção 4 de
 * {@code specs/06-api-contracts.md}. Os serviços não compartilham código, então o servidor é escrito aqui a
 * partir do contrato, não do código da {@code api-conhecimento}. As respostas da ferramenta são configuráveis:
 * sucesso, lista vazia, {@code isError}, resultado fora do schema, lentidão e falhas HTTP (401, 403, 500).
 */
public final class ServidorMcpDeTeste implements AutoCloseable {

    /** Uma requisição HTTP recebida, com cabeçalhos em minúsculas. */
    public record Requisicao(String metodoHttp, String metodoRpc, Map<String, String> cabecalhos, String corpo) {

        public String cabecalho(String nome) {
            return cabecalhos.get(nome.toLowerCase());
        }
    }

    public static final String TRECHO_1 = """
            {"id":"c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f","texto":"Para o curso Java Fullstack é recomendado conhecimento básico de lógica de programação.",
             "documentoId":"3f6c2a1e-8b4d-4c7a-9e1f-2b3c4d5e6f70","titulo":"Curso Java Fullstack — pré-requisitos",
             "fonte":"Site COTI — página do curso Java Fullstack, out/2026","categoria":"PRE_REQUISITO","similaridade":0.86}""";

    public static final String TRECHO_2 = """
            {"id":"e7f8a9b0-c1d2-4e3f-8a4b-5c6d7e8f9a0b","texto":"O curso de Angular é oferecido nas modalidades presencial e online ao vivo.",
             "documentoId":"5d6e7f8a-9b0c-4d1e-8f2a-3b4c5d6e7f8a","titulo":"Modalidades dos cursos",
             "fonte":"Manual do aluno COTI 2026, seção 2","categoria":"MODALIDADE","similaridade":0.83}""";

    private static final String FERRAMENTA = """
            {"name":"buscar_conhecimento","title":"Buscar conhecimento COTI",
             "description":"Busca trechos publicados da base de conhecimento oficial da COTI Informática semanticamente relacionados à pergunta.",
             "inputSchema":{"type":"object","properties":{"pergunta":{"type":"string","description":"Pergunta ou texto a pesquisar na base de conhecimento."},
               "limite":{"type":"integer","description":"Quantidade máxima de trechos a devolver."}},"required":["pergunta"]},
             "outputSchema":{"type":"object","properties":{"trechos":{"type":"array","items":{"type":"object","properties":{
               "id":{"type":"string","format":"uuid"},"texto":{"type":"string"},"documentoId":{"type":"string","format":"uuid"},
               "titulo":{"type":"string"},"fonte":{"type":"string"},
               "categoria":{"type":"string","enum":["CURSO","PROGRAMA","TECNOLOGIA","MODALIDADE","PRE_REQUISITO","INSTITUCIONAL"]},
               "similaridade":{"type":"number"}},"required":["id","texto","documentoId","titulo","fonte","categoria"]}},
               "total":{"type":"integer"}},"required":["trechos","total"]},
             "annotations":{"readOnlyHint":true,"destructiveHint":false,"idempotentHint":true,"openWorldHint":false}}""";

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpServer servidor;
    private final List<Requisicao> requisicoes = Collections.synchronizedList(new ArrayList<>());

    private volatile String chaveEsperada;
    private volatile String structuredContent = "{\"trechos\":[" + TRECHO_1 + "," + TRECHO_2 + "],\"total\":2}";
    private volatile boolean semStructuredContent;
    private volatile boolean erroDaFerramenta;
    private volatile String textoDoErro = "{\"codigo\":\"BASE_CONHECIMENTO_INDISPONIVEL\",\"mensagem\":\"A base de conhecimento está temporariamente indisponível.\"}";
    private volatile Duration atrasoNaChamada = Duration.ZERO;
    private volatile int statusHttpNaChamada;
    private volatile int statusHttpEmTudo;
    private volatile boolean semFerramenta;
    private volatile int chamadasLentas;
    private volatile String sessaoAtual;
    private final java.util.concurrent.atomic.AtomicInteger contadorDeSessoes = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger contadorDeChamadas = new java.util.concurrent.atomic.AtomicInteger();

    private ServidorMcpDeTeste(int porta) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", porta), 0);
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        servidor.createContext("/mcp", this::atender);
    }

    public static ServidorMcpDeTeste iniciar() {
        return iniciar(0);
    }

    /** Sobe na porta informada (para simular a volta do servidor ao mesmo endereço). */
    public static ServidorMcpDeTeste iniciar(int porta) {
        try {
            ServidorMcpDeTeste servidor = new ServidorMcpDeTeste(porta);
            servidor.servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível iniciar o servidor MCP de teste.", e);
        }
    }

    public int porta() {
        return servidor.getAddress().getPort();
    }

    public String url() {
        return "http://127.0.0.1:" + porta();
    }

    // ---- configuração do comportamento

    /** Passa a exigir {@code Authorization: Bearer <chave>}; sem ou com chave errada responde 401. */
    public ServidorMcpDeTeste exigindoBearer(String chave) {
        this.chaveEsperada = chave;
        return this;
    }

    /** O {@code structuredContent} devolvido pela ferramenta (JSON). O texto equivalente é gerado igual. */
    public ServidorMcpDeTeste respondendoCom(String structuredContentJson) {
        this.structuredContent = structuredContentJson;
        this.semStructuredContent = false;
        this.erroDaFerramenta = false;
        this.statusHttpNaChamada = 0;
        return this;
    }

    public ServidorMcpDeTeste respondendoComTrechos(String... trechosJson) {
        return respondendoCom("{\"trechos\":[" + String.join(",", trechosJson) + "],\"total\":" + trechosJson.length + "}");
    }

    public ServidorMcpDeTeste respondendoVazio() {
        return respondendoCom("{\"trechos\":[],\"total\":0}");
    }

    public ServidorMcpDeTeste respondendoSemStructuredContent() {
        this.semStructuredContent = true;
        this.erroDaFerramenta = false;
        return this;
    }

    public ServidorMcpDeTeste respondendoComErroDaFerramenta(String codigo) {
        this.erroDaFerramenta = true;
        this.textoDoErro = "{\"codigo\":\"" + codigo + "\",\"mensagem\":\"Falha simulada.\"}\n";
        return this;
    }

    public ServidorMcpDeTeste demorandoNaChamada(Duration atraso) {
        this.atrasoNaChamada = atraso;
        return this;
    }

    /** Só o {@code tools/call} devolve esse status HTTP (o handshake e o {@code tools/list} continuam normais). */
    public ServidorMcpDeTeste comStatusHttpNaChamada(int status) {
        this.statusHttpNaChamada = status;
        return this;
    }

    /** Toda requisição devolve esse status HTTP (por exemplo, 500 em vez de aceitar o handshake). */
    public ServidorMcpDeTeste comStatusHttpEmTudo(int status) {
        this.statusHttpEmTudo = status;
        return this;
    }

    /** Esquece a sessão atual, como um servidor que reiniciou: pedidos com o id antigo recebem 404 até um novo initialize. */
    public ServidorMcpDeTeste esquecendoAsSessoes() {
        this.sessaoAtual = null;
        return this;
    }

    /** O {@code tools/list} devolve uma lista vazia (servidor sem a ferramenta de busca). */
    public ServidorMcpDeTeste semAFerramenta() {
        this.semFerramenta = true;
        return this;
    }

    /** Só as primeiras {@code quantas} chamadas da ferramenta demoram {@code atraso}; as seguintes são normais. */
    public ServidorMcpDeTeste demorandoAsPrimeirasChamadas(int quantas, Duration atraso) {
        this.chamadasLentas = quantas;
        this.atrasoNaChamada = atraso;
        return this;
    }

    public ServidorMcpDeTeste reiniciar() {
        chaveEsperada = null;
        semFerramenta = false;
        chamadasLentas = 0;
        contadorDeChamadas.set(0);
        respondendoComTrechos(TRECHO_1, TRECHO_2);
        atrasoNaChamada = Duration.ZERO;
        statusHttpNaChamada = 0;
        statusHttpEmTudo = 0;
        requisicoes.clear();
        return this;
    }

    // ---- observação

    public List<Requisicao> requisicoes() {
        synchronized (requisicoes) {
            return List.copyOf(requisicoes);
        }
    }

    public List<Requisicao> chamadasDaFerramenta() {
        return requisicoes().stream().filter(r -> "tools/call".equals(r.metodoRpc())).toList();
    }

    /** Argumentos do {@code tools/call} número {@code indice} (a partir de 0). */
    public JsonNode argumentosDaChamada(int indice) {
        return json.readTree(chamadasDaFerramenta().get(indice).corpo()).get("params").get("arguments");
    }

    // ---- protocolo

    private void atender(HttpExchange troca) throws IOException {
        try (troca) {
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> cabecalhos = new LinkedHashMap<>();
            troca.getRequestHeaders().forEach((nome, valores) -> cabecalhos.put(nome.toLowerCase(), valores.get(0)));
            String metodoRpc = "POST".equals(troca.getRequestMethod()) && !corpo.isBlank() ? metodoRpc(corpo) : null;
            requisicoes.add(new Requisicao(troca.getRequestMethod(), metodoRpc, cabecalhos, corpo));

            if (!autorizado(cabecalhos)) {
                responder(troca, 401, "{\"codigo\":\"NAO_AUTENTICADO\"}");
                return;
            }
            if (statusHttpEmTudo != 0) {
                responder(troca, statusHttpEmTudo, "{}");
                return;
            }
            String sessaoRecebida = cabecalhos.get("mcp-session-id");
            if (sessaoRecebida != null && !sessaoRecebida.equals(sessaoAtual)) {
                responder(troca, 404, "{}");
                return;
            }
            if (!"POST".equals(troca.getRequestMethod())) {
                troca.sendResponseHeaders("DELETE".equals(troca.getRequestMethod()) ? 200 : 405, -1);
                return;
            }
            JsonNode requisicao = json.readTree(corpo);
            if (!requisicao.has("id")) {
                troca.sendResponseHeaders(202, -1);
                return;
            }
            Object resultado = switch (metodoRpc) {
                case "initialize" -> inicializar(requisicao);
                case "tools/list" -> Map.of("tools", semFerramenta ? List.of() : List.of(json.readTree(FERRAMENTA)));
                case "ping" -> Map.of();
                case "tools/call" -> {
                    dormir();
                    if (statusHttpNaChamada != 0) {
                        responder(troca, statusHttpNaChamada, "{}");
                        yield null;
                    }
                    yield chamarFerramenta();
                }
                default -> null;
            };
            if (resultado == null) {
                if (!troca.getResponseHeaders().containsKey("Content-Type")) {
                    responder(troca, 200, json.writeValueAsString(Map.of("jsonrpc", "2.0", "id", requisicao.get("id"),
                            "error", Map.of("code", -32601, "message", "Método não suportado"))));
                }
                return;
            }
            if ("initialize".equals(metodoRpc)) {
                sessaoAtual = "sessao-" + contadorDeSessoes.incrementAndGet();
            }
            if (sessaoAtual != null) {
                troca.getResponseHeaders().add("Mcp-Session-Id", sessaoAtual);
            }
            responder(troca, 200, json.writeValueAsString(Map.of("jsonrpc", "2.0", "id", requisicao.get("id"),
                    "result", resultado)));
        }
    }

    private boolean autorizado(Map<String, String> cabecalhos) {
        String esperada = chaveEsperada;
        return esperada == null || ("Bearer " + esperada).equals(cabecalhos.get("authorization"));
    }

    private String metodoRpc(String corpo) {
        try {
            return json.readTree(corpo).path("method").asString("");
        } catch (RuntimeException e) {
            return "";
        }
    }

    private Map<String, Object> inicializar(JsonNode requisicao) {
        return Map.of("protocolVersion", requisicao.get("params").get("protocolVersion").asString(),
                "capabilities", Map.of("tools", Map.of("listChanged", false)),
                "serverInfo", Map.of("name", "coti-conhecimento", "version", "1.0.0"));
    }

    private Map<String, Object> chamarFerramenta() {
        if (erroDaFerramenta) {
            return Map.of("isError", true, "content", List.of(Map.of("type", "text", "text", textoDoErro)));
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("isError", false);
        resultado.put("content", List.of(Map.of("type", "text", "text", structuredContent)));
        if (!semStructuredContent) {
            resultado.put("structuredContent", json.readTree(structuredContent));
        }
        return resultado;
    }

    private void dormir() {
        Duration atraso = atrasoNaChamada;
        int numero = contadorDeChamadas.incrementAndGet();
        if (!atraso.isZero() && (chamadasLentas == 0 || numero <= chamadasLentas)) {
            try {
                Thread.sleep(atraso);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void responder(HttpExchange troca, int status, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", "application/json");
        troca.sendResponseHeaders(status, bytes.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
