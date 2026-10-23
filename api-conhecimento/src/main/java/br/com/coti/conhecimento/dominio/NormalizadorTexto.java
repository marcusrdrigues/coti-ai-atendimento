package br.com.coti.conhecimento.dominio;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Normaliza o texto de um documento antes de analisar, calcular o hash e indexar: Unicode NFC, quebras de
 * linha em {@code \n}, sem caracteres de controle nem de formatação invisíveis (que poderiam esconder
 * instruções), espaços repetidos reduzidos e no máximo uma linha em branco seguida.
 */
public final class NormalizadorTexto {

    private static final Pattern ESPACOS_HORIZONTAIS = Pattern.compile("[\\p{Zs}\\t]+");
    private static final Pattern ESPACO_ANTES_DE_QUEBRA = Pattern.compile(" *\\n *");
    private static final Pattern MUITAS_QUEBRAS = Pattern.compile("\\n{3,}");

    private NormalizadorTexto() {
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String resultado = Normalizer.normalize(texto, Normalizer.Form.NFC)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(' ', '\n')
                .replace(' ', '\n');
        resultado = removerInvisiveis(resultado);
        resultado = ESPACOS_HORIZONTAIS.matcher(resultado).replaceAll(" ");
        resultado = ESPACO_ANTES_DE_QUEBRA.matcher(resultado).replaceAll("\n");
        resultado = MUITAS_QUEBRAS.matcher(resultado).replaceAll("\n\n");
        return resultado.strip();
    }

    /** Remove controles (exceto quebra de linha e tabulação) e caracteres de formatação (categoria Cf). */
    private static String removerInvisiveis(String texto) {
        StringBuilder limpo = new StringBuilder(texto.length());
        texto.codePoints().forEach(cp -> {
            int tipo = Character.getType(cp);
            boolean controle = tipo == Character.CONTROL && cp != '\n' && cp != '\t';
            boolean formatacao = tipo == Character.FORMAT;
            boolean substitutoSolto = tipo == Character.SURROGATE; // par incompleto de UTF-16
            if (!controle && !formatacao && !substitutoSolto) {
                limpo.appendCodePoint(cp);
            }
        });
        return limpo.toString();
    }
}
