package br.com.coti.conhecimento.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 em hexadecimal minúsculo (64 caracteres) do texto já normalizado; identifica conteúdo repetido. */
public final class HashConteudo {

    private HashConteudo() {
    }

    public static String sha256(String textoNormalizado) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256")
                    .digest(textoNormalizado.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
