package br.com.coti.conhecimento.dominio;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Identificador determinístico de um trecho: UUID versão 5 (RFC 4122, SHA-1) de {@code documentoId:ordem}.
 * Regravar o mesmo trecho produz o mesmo id, o que torna a gravação idempotente (o PgVectorStore atualiza a
 * linha existente em vez de duplicá-la).
 */
public final class IdTrecho {

    /** Espaço de nomes fixo do projeto; mudá-lo muda todos os ids. */
    private static final UUID ESPACO_DE_NOMES = UUID.fromString("a3b1f4c2-7d58-4e19-9b6a-0c2d4e6f8a10");

    private IdTrecho() {
    }

    public static UUID de(UUID documentoId, int ordem) {
        byte[] nome = (documentoId + ":" + ordem).getBytes(StandardCharsets.UTF_8);
        byte[] hash = sha1(ESPACO_DE_NOMES, nome);
        hash[6] = (byte) ((hash[6] & 0x0f) | 0x50); // versão 5
        hash[8] = (byte) ((hash[8] & 0x3f) | 0x80); // variante RFC 4122
        ByteBuffer buffer = ByteBuffer.wrap(hash, 0, 16);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static byte[] sha1(UUID espaco, byte[] nome) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(ByteBuffer.allocate(16)
                    .putLong(espaco.getMostSignificantBits()).putLong(espaco.getLeastSignificantBits()).array());
            return sha1.digest(nome);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 indisponível na JVM", e);
        }
    }
}
