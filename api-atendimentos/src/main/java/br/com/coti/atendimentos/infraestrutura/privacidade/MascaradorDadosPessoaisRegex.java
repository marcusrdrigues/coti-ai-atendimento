package br.com.coti.atendimentos.infraestrutura.privacidade;

import br.com.coti.atendimentos.aplicacao.porta.MascaradorPort;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Mascara CPF, e-mail e telefone brasileiro por expressões regulares. Na dúvida mascara: um CPF com pontuação é
 * mascarado mesmo com dígito verificador errado (é dado pessoal digitado errado); sem pontuação só quando o
 * dígito confere, para não esconder números de protocolo. Telefone de dez dígitos corridos, sem DDD entre
 * parênteses, "+55" nem separadores, não é mascarado (é ambíguo com protocolos e códigos).
 */
@Component
public final class MascaradorDadosPessoaisRegex implements MascaradorPort {

    static final String CPF_MASCARADO = "[CPF]";
    static final String EMAIL_MASCARADO = "[EMAIL]";
    static final String TELEFONE_MASCARADO = "[TELEFONE]";

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");
    private static final Pattern CPF = Pattern.compile("(?<!\\d)(\\d{3})(\\.?)(\\d{3})(\\.?)(\\d{3})(-?)(\\d{2})(?!\\d)");
    private static final Pattern TELEFONE = Pattern.compile(
            "(?<![\\d.,])(?:\\+?55[\\s.-]?)?(\\(\\d{2}\\)|\\d{2})[\\s.-]?(9?\\d{4})[\\s.-]?(\\d{4})(?![\\d])");

    @Override
    public String mascarar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        String semEmail = EMAIL.matcher(texto).replaceAll(EMAIL_MASCARADO);
        String semCpf = mascararCpf(semEmail);
        return mascararTelefone(semCpf);
    }

    private static String mascararCpf(String texto) {
        Matcher m = CPF.matcher(texto);
        StringBuilder resultado = new StringBuilder();
        while (m.find()) {
            String digitos = m.group(1) + m.group(3) + m.group(5) + m.group(7);
            boolean pontuado = !m.group(2).isEmpty() && !m.group(4).isEmpty() && !m.group(6).isEmpty();
            m.appendReplacement(resultado, Matcher.quoteReplacement(
                    pontuado || cpfValido(digitos) ? CPF_MASCARADO : m.group()));
        }
        m.appendTail(resultado);
        return resultado.toString();
    }

    private static String mascararTelefone(String texto) {
        Matcher m = TELEFONE.matcher(texto);
        StringBuilder resultado = new StringBuilder();
        while (m.find()) {
            String bruto = m.group();
            String nacional = apenasDigitos(bruto).replaceFirst("^55(?=\\d{10,11}$)", "");
            boolean telefone = dddValido(apenasDigitos(m.group(1))) && pareceTelefone(bruto, nacional);
            m.appendReplacement(resultado, Matcher.quoteReplacement(telefone ? TELEFONE_MASCARADO : bruto));
        }
        m.appendTail(resultado);
        return resultado.toString();
    }

    /** Dígitos verificadores do CPF; recusa sequências repetidas (111.111.111-11). */
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
