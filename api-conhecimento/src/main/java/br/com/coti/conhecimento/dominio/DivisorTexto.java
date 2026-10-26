package br.com.coti.conhecimento.dominio;

import br.com.coti.conhecimento.dominio.excecao.DocumentoGrandeDemaisException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Divide o texto normalizado de um documento em trechos de até {@code tamanhoMaximo} caracteres, com
 * sobreposição entre vizinhos (RF02). Código puro: mesma entrada e mesma configuração geram os mesmos
 * trechos e os mesmos ids.
 *
 * <p>Por que não o {@code TokenTextSplitter} do Spring AI: ele não tem sobreposição (ADR-09).
 *
 * <p>Regras do corte, em ordem de preferência, dentro da janela de busca: fim de parágrafo, fim de frase,
 * quebra de linha, espaço; só sem nenhum deles corta no meio de uma palavra. Cada trecho seguinte começa até
 * {@code sobreposicao} caracteres antes do fim do anterior, sempre no início de uma palavra. O último trecho
 * nunca é descartado por ser curto: isso perderia o fim do documento.
 */
public final class DivisorTexto {

    private final ConfiguracaoDivisao configuracao;

    public DivisorTexto(ConfiguracaoDivisao configuracao) {
        this.configuracao = configuracao;
    }

    /**
     * @throws DocumentoGrandeDemaisException se o texto gerar mais trechos que o máximo configurado
     */
    public List<Trecho> dividir(UUID documentoId, String texto) {
        String t = texto == null ? "" : texto.strip();
        List<Trecho> trechos = new ArrayList<>();
        if (t.isEmpty()) {
            return trechos;
        }
        int n = t.length();
        int inicio = 0;
        while (true) {
            int fimJanela = Math.min(inicio + configuracao.tamanhoMaximo(), n);
            int corte = fimJanela == n ? n : pontoDeCorte(t, inicio, fimJanela);
            adicionar(trechos, documentoId, t.substring(inicio, corte));
            if (corte >= n) {
                return trechos;
            }
            inicio = proximoInicio(t, inicio, corte);
        }
    }

    private void adicionar(List<Trecho> trechos, UUID documentoId, String bruto) {
        String texto = bruto.strip();
        if (texto.isEmpty()) {
            return;
        }
        if (trechos.size() >= configuracao.maximoTrechos()) {
            throw new DocumentoGrandeDemaisException(configuracao.maximoTrechos());
        }
        int ordem = trechos.size();
        trechos.add(new Trecho(IdTrecho.de(documentoId, ordem), ordem, texto));
    }

    /** Menor posição de corte aceitável: evita trechos menores que o mínimo e garante avanço com a sobreposição. */
    private int limiteInferior(int inicio) {
        int minimo = Math.max(configuracao.tamanhoMinimo(), configuracao.sobreposicao() + 1);
        return inicio + Math.max(minimo, configuracao.tamanhoMaximo() / 2);
    }

    private int pontoDeCorte(String t, int inicio, int fim) {
        int inferior = limiteInferior(inicio);
        if (inferior >= fim) {
            return fim;
        }
        int paragrafo = t.lastIndexOf("\n\n", fim);
        if (paragrafo >= inferior) {
            return paragrafo;
        }
        for (int i = fim; i >= inferior; i--) {
            if (i < t.length() && fimDeFrase(t, i)) {
                return i;
            }
        }
        int linha = t.lastIndexOf('\n', fim);
        if (linha >= inferior) {
            return linha;
        }
        int espaco = t.lastIndexOf(' ', fim);
        if (espaco >= inferior) {
            return espaco;
        }
        return fim;
    }

    /** Posição logo depois de {@code . ? !} e antes de um espaço em branco. */
    private static boolean fimDeFrase(String t, int i) {
        char anterior = t.charAt(i - 1);
        return (anterior == '.' || anterior == '?' || anterior == '!') && Character.isWhitespace(t.charAt(i));
    }

    /**
     * Começa até {@code sobreposicao} caracteres antes do corte, no início de uma palavra. Sem espaço nessa
     * região (palavra gigante), não há sobreposição; em qualquer caso o início avança.
     */
    private int proximoInicio(String t, int inicioAtual, int corte) {
        int proximo = Math.max(corte - configuracao.sobreposicao(), 0);
        while (proximo > 0 && proximo < corte && !Character.isWhitespace(t.charAt(proximo - 1))) {
            proximo++;
        }
        if (proximo <= inicioAtual || proximo >= corte) {
            return corte;
        }
        return proximo;
    }
}
