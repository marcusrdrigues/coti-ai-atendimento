package br.com.coti.conhecimento.infraestrutura.analise;

import br.com.coti.conhecimento.aplicacao.porta.AnalisadorConteudoPort;
import br.com.coti.conhecimento.dominio.Pendencia;
import br.com.coti.conhecimento.dominio.ResultadoAnalise;
import br.com.coti.conhecimento.infraestrutura.config.PropriedadesConhecimento;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Análise por expressões regulares. É uma camada que falha (Artigo 10): detecta dado pessoal com validação
 * (CPF com dígito verificador, e-mail e telefone brasileiro) e sinais de prompt injection configuráveis. A
 * defesa principal continua sendo o modelo sem privilégios.
 */
@Component
public final class AnalisadorConteudoRegex implements AnalisadorConteudoPort {

    private static final Pattern CPF = Pattern.compile("(?<!\\d)(\\d{3})\\.?(\\d{3})\\.?(\\d{3})-?(\\d{2})(?!\\d)");
    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@([A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,})");
    // 10 ou 11 dígitos, com DDD e separadores opcionais; "+55" opcional. Não casa dentro de números maiores.
    private static final Pattern TELEFONE = Pattern.compile(
            "(?<![\\d.,])(?:\\+?55[\\s.-]?)?(\\(\\d{2}\\)|\\d{2})[\\s.-]?(9?\\d{4})[\\s.-]?(\\d{4})(?![\\d])");

    private final List<Pattern> padroesInjecao;
    private final Set<String> dominiosEmailPermitidos;
    private final Set<String> telefonesPermitidos;

    public AnalisadorConteudoRegex(PropriedadesConhecimento propriedades) {
        PropriedadesConhecimento.Analise analise = propriedades.analise();
        this.padroesInjecao = analise.padroesInjecao().stream()
                .map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
                .toList();
        this.dominiosEmailPermitidos = analise.dominiosEmailPermitidos().stream()
                .map(d -> d.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.telefonesPermitidos = analise.telefonesPermitidos().stream()
                .map(AnalisadorConteudoRegex::apenasDigitos).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public ResultadoAnalise analisar(String texto) {
        boolean dadoPessoal = contemCpf(texto) || contemEmailPessoal(texto) || contemTelefonePessoal(texto);
        List<Pendencia> pendencias = padroesInjecao.stream().anyMatch(p -> p.matcher(texto).find())
                ? List.of(Pendencia.POSSIVEL_PROMPT_INJECTION)
                : List.of();
        return new ResultadoAnalise(dadoPessoal, pendencias);
    }

    private static boolean contemCpf(String texto) {
        Matcher m = CPF.matcher(texto);
        while (m.find()) {
            if (cpfValido(m.group(1) + m.group(2) + m.group(3) + m.group(4))) {
                return true;
            }
        }
        return false;
    }

    /** Dígitos verificadores do CPF; recusa sequências de dígitos repetidos (ex.: 111.111.111-11). */
    static boolean cpfValido(String digitos) {
        if (digitos.length() != 11 || digitos.chars().distinct().count() == 1) {
            return false;
        }
        return digito(digitos, 9) == digitos.charAt(9) - '0' && digito(digitos, 10) == digitos.charAt(10) - '0';
    }

    private static int digito(String cpf, int posicao) {
        int soma = 0;
        for (int i = 0; i < posicao; i++) {
            soma += (cpf.charAt(i) - '0') * (posicao + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    private boolean contemEmailPessoal(String texto) {
        Matcher m = EMAIL.matcher(texto);
        while (m.find()) {
            if (!dominiosEmailPermitidos.contains(m.group(1).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean contemTelefonePessoal(String texto) {
        Matcher m = TELEFONE.matcher(texto);
        while (m.find()) {
            String bruto = m.group();
            String nacional = apenasDigitos(bruto).replaceFirst("^55(?=\\d{10,11}$)", "");
            if (dddValido(apenasDigitos(m.group(1))) && pareceTelefone(bruto, nacional)
                    && !telefonesPermitidos.contains(nacional)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dez dígitos corridos (sem parênteses, "+" nem separadores) são ambíguos: protocolos e códigos têm o mesmo
     * formato. Só contam como telefone se houver sinal de telefone: DDD entre parênteses, "+55", separadores entre
     * os grupos ou celular de 11 dígitos (o nono dígito começa por 9).
     */
    /**
     * Dez dígitos corridos (sem parênteses, "+" nem separadores) são ambíguos: protocolos e códigos têm o mesmo
     * formato. Só contam como telefone se houver sinal de telefone: DDD entre parênteses, "+55", separadores
     * entre os grupos ou celular de 11 dígitos (o nono dígito começa por 9).
     */
    private static boolean pareceTelefone(String bruto, String nacional) {
        boolean celular = nacional.length() == 11 && nacional.charAt(2) == '9';
        return bruto.startsWith("+") || bruto.startsWith("(") || bruto.matches(".*[\\s.-].*") || celular;
    }

    private static boolean dddValido(String ddd) {
        int valor = Integer.parseInt(ddd);
        return valor >= 11 && valor <= 99 && ddd.charAt(1) != '0';
    }

    private static String apenasDigitos(String valor) {
        return valor.replaceAll("\\D", "");
    }
}
