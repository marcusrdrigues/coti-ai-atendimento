package br.com.coti.atendimentos.aplicacao;

import br.com.coti.atendimentos.dominio.ContextoRag;
import br.com.coti.atendimentos.dominio.ReferenciaTrecho;
import br.com.coti.atendimentos.dominio.RespostaGerada;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida em código a saída do modelo (contrato, seção 6.4), nesta ordem: resposta presente e sem rótulos
 * {@code T<n>}; toda referência existe no contexto; {@code conhecimentoSuficiente = true} exige referência; e
 * valores monetários, descontos, parcelas, datas, horários e número de vagas da resposta aparecem literalmente no
 * texto de algum trecho referenciado (Artigo 12). Devolve o motivo da reprovação, que volta ao modelo na nova
 * tentativa, ou vazio quando aprovada. É uma camada que falha: reprova de forma conservadora e nunca "conserta".
 *
 * <p>Só padrões de valor entram na checagem de evidência. Números comuns ("2 anos de experiência", "40 horas")
 * não reprovam.
 */
public final class ValidadorRespostaModelo {

    private static final String MESES = "janeiro|fevereiro|mar[cç]o|abril|maio|junho|julho|agosto|setembro|outubro"
            + "|novembro|dezembro";

    private static final int OPCOES = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** Rótulo dentro de uma resposta ao aluno: {@code T1}, {@code (T2)}, {@code [T3]}. */
    private static final Pattern ROTULO = Pattern.compile("(?<![\\p{L}\\p{N}])T\\d+(?![\\p{L}\\p{N}])");

    /** Padrões de valor que exigem evidência literal no trecho referenciado. */
    private static final List<Pattern> VALORES = List.of(
            Pattern.compile("R\\$\\s*\\d[\\d.]*(?:,\\d{1,2})?", OPCOES),
            Pattern.compile("\\d[\\d.]*(?:,\\d{1,2})?\\s*reais", OPCOES),
            Pattern.compile("\\d{1,3}(?:,\\d{1,2})?\\s*%"),
            Pattern.compile("(?<![\\p{L}\\p{N}])\\d{1,2}\\s*x(?![\\p{L}\\p{N}])", OPCOES),
            Pattern.compile("\\d{1,2}\\s+parcelas?", OPCOES),
            Pattern.compile("\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?"),
            Pattern.compile("\\d{1,2}\\s+de\\s+(?:" + MESES + ")(?:\\s+de\\s+\\d{4})?", OPCOES),
            Pattern.compile("\\d{1,2}:\\d{2}"),
            Pattern.compile("(?<![\\p{L}\\p{N}])\\d{1,2}h(?:\\d{2})?(?![\\p{L}\\p{N}])", OPCOES),
            Pattern.compile("\\d+\\s+vagas?", OPCOES),
            Pattern.compile("vagas?\\s*:\\s*\\d+", OPCOES));

    public Optional<String> validar(RespostaGerada gerada, ContextoRag contexto) {
        if (gerada == null || gerada.resposta() == null || gerada.resposta().isBlank()) {
            return Optional.of("A resposta está vazia. Escreva a resposta ao aluno em português.");
        }
        if (!gerada.conhecimentoSuficiente()) {
            return Optional.empty(); // o texto do modelo não será usado: a recusa configurada o substitui
        }
        Matcher rotulo = ROTULO.matcher(gerada.resposta());
        if (rotulo.find()) {
            return Optional.of("A resposta cita o rótulo " + rotulo.group() + ". Os rótulos T1, T2... são só para o "
                    + "campo referencias; não os mencione no texto da resposta.");
        }
        if (gerada.referencias().isEmpty()) {
            return Optional.of("conhecimentoSuficiente é verdadeiro, mas referencias está vazio. Liste os rótulos dos "
                    + "trechos usados, ou marque conhecimentoSuficiente como falso.");
        }
        List<ReferenciaTrecho> citadas = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (String referencia : gerada.referencias()) {
            Optional<ReferenciaTrecho> encontrada = referencia == null ? Optional.empty() : contexto.buscar(referencia);
            if (encontrada.isEmpty()) {
                return Optional.of("A referência " + descrever(referencia) + " não existe. Use somente os rótulos dos "
                        + "trechos fornecidos: " + rotulosDisponiveis(contexto) + ".");
            }
            if (vistos.add(referencia)) {
                citadas.add(encontrada.get());
            }
        }
        return valorSemEvidencia(gerada.resposta(), citadas)
                .map(valor -> "O valor \"" + valor + "\" da resposta não aparece nos trechos referenciados. Informe "
                        + "somente preços, descontos, parcelas, datas, horários e vagas escritos nos trechos.");
    }

    private static Optional<String> valorSemEvidencia(String resposta, List<ReferenciaTrecho> citadas) {
        String evidencia = citadas.stream().map(r -> normalizar(r.textoExibido())).reduce("", (a, b) -> a + "\n" + b);
        for (Pattern padrao : VALORES) {
            Matcher m = padrao.matcher(resposta);
            while (m.find()) {
                if (!evidencia.contains(normalizar(m.group()))) {
                    return Optional.of(m.group().strip());
                }
            }
        }
        return Optional.empty();
    }

    /** Sem espaços, caixa baixa e sem acento: "15 de Março" e "15demarco" são o mesmo valor. */
    static String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return semAcento.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String rotulosDisponiveis(ContextoRag contexto) {
        return contexto.referencias().stream().map(ReferenciaTrecho::rotulo).toList().toString();
    }

    /** Descreve a referência inválida sem repassar texto arbitrário do modelo. */
    private static String descrever(String referencia) {
        if (referencia == null) {
            return "(vazia)";
        }
        String limpa = referencia.length() > 20 ? referencia.substring(0, 20) : referencia;
        return limpa.replaceAll("[^\\p{L}\\p{N}_ -]", "?");
    }
}
