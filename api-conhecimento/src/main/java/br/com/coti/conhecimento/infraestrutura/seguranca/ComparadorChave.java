package br.com.coti.conhecimento.infraestrutura.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Compara chaves de API em tempo constante (resumo SHA-256 + {@link MessageDigest#isEqual}). */
public final class ComparadorChave {

    private ComparadorChave() {
    }

    /** Chave esperada em branco nunca é aceita, mesmo que a recebida também esteja em branco. */
    public static boolean iguais(String recebida, String esperada) {
        if (recebida == null || esperada == null || esperada.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(resumo(recebida), resumo(esperada));
    }

    private static byte[] resumo(String valor) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
