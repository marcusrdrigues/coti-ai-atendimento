package br.com.coti.atendimentos.api.erro;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escreve o erro padronizado diretamente na resposta, para uso em filtros e na segurança, onde o
 * tratamento de exceções do Spring MVC não alcança.
 */
@Component
public class EscritorProblema {

    private final JsonMapper jsonMapper;

    public EscritorProblema(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void escrever(HttpServletRequest request, HttpServletResponse response, CodigoErro codigo)
            throws IOException {
        ProblemDetail problema = ProblemaFactory.criar(codigo, request);
        response.setStatus(codigo.status().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), comoMapa(problema));
    }

    static Map<String, Object> comoMapa(ProblemDetail problema) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("type", String.valueOf(problema.getType()));
        mapa.put("title", problema.getTitle());
        mapa.put("status", problema.getStatus());
        mapa.put("detail", problema.getDetail());
        mapa.put("instance", String.valueOf(problema.getInstance()));
        if (problema.getProperties() != null) {
            mapa.putAll(problema.getProperties());
        }
        return mapa;
    }
}
