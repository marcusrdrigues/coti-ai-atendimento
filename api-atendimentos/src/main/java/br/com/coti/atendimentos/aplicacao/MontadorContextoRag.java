package br.com.coti.atendimentos.aplicacao;

import br.com.coti.atendimentos.dominio.ContextoRag;
import br.com.coti.atendimentos.dominio.ReferenciaTrecho;
import br.com.coti.atendimentos.dominio.TrechoConhecimento;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monta o contexto que o modelo recebe (contrato, seção 6.3), em código puro e testável: descarta o que está abaixo
 * do limiar, ordena por similaridade, sanitiza, limita o tamanho e rotula os trechos {@code T1}, {@code T2}, ...
 *
 * <p>O texto dos trechos vem da base e pode conter instrução maliciosa: é DADO. Aqui ele não ganha autoridade de
 * instrução, porque nenhuma sequência que imite os delimitadores do prompt sobrevive (a estrutura do prompt não
 * pode ser forjada de dentro de um trecho) e os valores de atributo não escapam das aspas. A defesa de verdade
 * continua sendo o modelo sem ferramentas nem privilégios.
 */
public final class MontadorContextoRag {

    /** Limites do contexto (padrões do contrato: 0,70, 2.000 por trecho, 8.000 no total, 5 trechos). */
    public record Limites(double limiarSimilaridade, int maximoCaracteresTrecho, int maximoCaracteresTotal,
                          int maximoTrechos) {

        public Limites {
            if (limiarSimilaridade < 0 || limiarSimilaridade > 1 || maximoCaracteresTrecho < 1
                    || maximoCaracteresTotal < maximoCaracteresTrecho || maximoTrechos < 1) {
                throw new IllegalArgumentException("limites do contexto incoerentes");
            }
        }

        public static Limites padrao() {
            return new Limites(0.70, 2_000, 8_000, 5);
        }
    }

    /** Início de tag que imita um delimitador do prompt: {@code <trechos}, {@code </trecho}, {@code < pergunta}... */
    private static final Pattern DELIMITADOR = Pattern.compile("<\\s*/?\\s*(?:trechos?|pergunta)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FIM_DE_FRASE = Pattern.compile("[.!?](?=\\s|$)");

    private final Limites limites;

    public MontadorContextoRag(Limites limites) {
        this.limites = limites;
    }

    public ContextoRag montar(List<TrechoConhecimento> trechos) {
        List<TrechoConhecimento> relevantes = ordenarPorSimilaridade(descartarAbaixoDoLimiar(trechos));

        List<ReferenciaTrecho> aceitos = new ArrayList<>();
        int soma = 0;
        for (TrechoConhecimento trecho : relevantes) {
            if (aceitos.size() >= limites.maximoTrechos()) {
                break;
            }
            String texto = cortar(sanitizarTexto(trecho.texto()), limites.maximoCaracteresTrecho());
            if (texto.isBlank()) {
                continue;
            }
            if (soma + texto.length() > limites.maximoCaracteresTotal()) {
                break; // sai inteiro o de menor similaridade; nunca um trecho cortado pela metade
            }
            soma += texto.length();
            aceitos.add(new ReferenciaTrecho("T" + (aceitos.size() + 1), trecho, texto));
        }
        if (aceitos.isEmpty()) {
            return ContextoRag.semTrechos();
        }
        return new ContextoRag(aceitos, bloco(aceitos));
    }

    private List<TrechoConhecimento> descartarAbaixoDoLimiar(List<TrechoConhecimento> trechos) {
        return trechos.stream()
                .filter(t -> t.similaridade() == null || t.similaridade() >= limites.limiarSimilaridade())
                .toList();
    }

    /** Maior similaridade primeiro; sem similaridade vai para o fim, mantendo a ordem do servidor (ordenação estável). */
    private static List<TrechoConhecimento> ordenarPorSimilaridade(List<TrechoConhecimento> trechos) {
        List<TrechoConhecimento> ordenados = new ArrayList<>(trechos);
        ordenados.sort(Comparator.comparing(TrechoConhecimento::similaridade,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return ordenados;
    }

    private static String bloco(List<ReferenciaTrecho> referencias) {
        StringBuilder bloco = new StringBuilder("<trechos>\n");
        for (ReferenciaTrecho r : referencias) {
            bloco.append("<trecho ref=\"").append(r.rotulo())
                    .append("\" titulo=\"").append(atributo(r.trecho().titulo()))
                    .append("\" fonte=\"").append(atributo(r.trecho().fonte()))
                    .append("\" categoria=\"").append(r.trecho().categoria().name()).append("\">\n")
                    .append(r.textoExibido()).append('\n')
                    .append("</trecho>\n");
        }
        return bloco.append("</trechos>").toString();
    }

    // ---- sanitização

    /** Texto do trecho: sem caracteres de controle (exceto quebra de linha) e sem delimitadores forjados. */
    static String sanitizarTexto(String texto) {
        StringBuilder limpo = new StringBuilder(texto.length());
        texto.codePoints().forEach(c -> {
            if (c == '\n') {
                limpo.append('\n');
            } else if (c == '\t') {
                limpo.append(' ');
            } else if (!Character.isISOControl(c)) {
                limpo.appendCodePoint(c);
            }
        });
        return neutralizarDelimitadores(limpo.toString()).strip();
    }

    /** Valor de atributo: uma linha, sem controle, sem delimitadores e sem aspas nem {@code &} soltos. */
    static String atributo(String valor) {
        String umaLinha = sanitizarTexto(valor).replace('\n', ' ');
        return umaLinha.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "‹").replace(">", "›");
    }

    /**
     * Troca {@code <} por {@code ‹} no início de cada tag que imita um delimitador do prompt e o {@code >} que a
     * fecha por {@code ›}.
     */
    static String neutralizarDelimitadores(String texto) {
        Matcher m = DELIMITADOR.matcher(texto);
        StringBuilder resultado = new StringBuilder();
        int posicao = 0;
        while (m.find()) {
            resultado.append(texto, posicao, m.start()).append('‹').append(texto, m.start() + 1, m.end());
            posicao = m.end();
            int fecha = texto.indexOf('>', posicao);
            int abre = texto.indexOf('<', posicao);
            if (fecha >= 0 && (abre < 0 || fecha < abre)) {
                resultado.append(texto, posicao, fecha).append('›');
                posicao = fecha + 1;
            }
        }
        return resultado.append(texto, posicao, texto.length()).toString();
    }

    // ---- tamanho

    /** Corta em {@code maximo} caracteres, preferindo o último fim de frase da segunda metade do texto. */
    static String cortar(String texto, int maximo) {
        if (texto.length() <= maximo) {
            return texto;
        }
        String janela = texto.substring(0, maximo);
        Matcher m = FIM_DE_FRASE.matcher(janela);
        int ultimoFim = -1;
        while (m.find()) {
            ultimoFim = m.end();
        }
        String cortado = ultimoFim >= maximo / 2 ? janela.substring(0, ultimoFim) : janela;
        return cortado.strip();
    }
}
