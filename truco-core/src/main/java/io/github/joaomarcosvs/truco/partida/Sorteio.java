package io.github.joaomarcosvs.truco.partida;

import io.github.joaomarcosvs.truco.carta.Carta;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sorteios da partida, derivados só da seed (CLAUDE.md, princípio 3): o carteador inicial e o embaralhamento de cada
 * rodada. Os números vêm de SHA-256 em modo contador, então a sequência é a mesma em qualquer JVM e conhecer parte dela
 * não ajuda a prever o resto.
 */
final class Sorteio {

    private Sorteio() {}

    /** Posição, na ordem da mesa, de quem dá as cartas na 1ª rodada (RG-PARTIDA-4). */
    static int carteadorInicial(long seed, int quantidadeDeJogadores) {
        return new Gerador(seed, "carteador", 0).proximo(quantidadeDeJogadores);
    }

    /** As cartas embaralhadas para a rodada (Fisher–Yates). */
    static List<Carta> embaralhar(List<Carta> cartas, long seed, int numeroDaRodada) {
        Gerador gerador = new Gerador(seed, "embaralhamento", numeroDaRodada);
        List<Carta> embaralhadas = new ArrayList<>(cartas);
        for (int i = embaralhadas.size() - 1; i > 0; i--) {
            Collections.swap(embaralhadas, i, gerador.proximo(i + 1));
        }
        return List.copyOf(embaralhadas);
    }

    /** Números pseudoaleatórios: blocos de SHA-256(finalidade, seed, contexto, número do bloco). */
    private static final class Gerador {

        private static final long FAIXA_DE_INT = 1L << Integer.SIZE;

        private final MessageDigest sha256;
        private final byte[] prefixo;
        private long numeroDoBloco;
        private ByteBuffer bloco = ByteBuffer.allocate(0);

        Gerador(long seed, String finalidade, int contexto) {
            sha256 = novoSha256();
            byte[] nome = finalidade.getBytes(StandardCharsets.UTF_8);
            prefixo = ByteBuffer.allocate(nome.length + 1 + Long.BYTES + Integer.BYTES)
                    .put(nome)
                    .put((byte) 0)
                    .putLong(seed)
                    .putInt(contexto)
                    .array();
        }

        /** Inteiro uniforme de 0 (inclusive) até o limite (exclusive), sem viés: valores do resto são descartados. */
        int proximo(int limite) {
            if (limite < 1) {
                throw new IllegalArgumentException("O limite precisa ser positivo: " + limite);
            }
            long aceitaveis = FAIXA_DE_INT - FAIXA_DE_INT % limite;
            while (true) {
                long valor = Integer.toUnsignedLong(proximoInt());
                if (valor < aceitaveis) {
                    return (int) (valor % limite);
                }
            }
        }

        private int proximoInt() {
            if (!bloco.hasRemaining()) {
                sha256.update(prefixo);
                sha256.update(
                        ByteBuffer.allocate(Long.BYTES).putLong(numeroDoBloco++).array());
                bloco = ByteBuffer.wrap(sha256.digest());
            }
            return bloco.getInt();
        }

        private static MessageDigest novoSha256() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Toda JVM precisa oferecer SHA-256", e);
            }
        }
    }
}
