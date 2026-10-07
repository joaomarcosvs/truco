package io.github.joaomarcosvs.truco.regras;

import io.github.joaomarcosvs.truco.carta.Carta;
import java.util.Optional;

/**
 * Força das cartas na disputa de uma vaza (RG-CARTAS-2 a RG-CARTAS-6 no Truco Paulista). A força pode depender da vira
 * ou não: variantes de manilhas fixas recebem a vira vazia.
 */
public interface OrdemDeForca {

    /**
     * Força da carta na rodada: quanto maior, mais forte, e cartas de mesma força empatam. O número só serve para
     * comparar cartas da mesma rodada.
     */
    int forca(Carta carta, Optional<Carta> vira);

    /** Compara duas cartas: negativo se {@code a} perde, zero se empatam, positivo se {@code a} vence. */
    default int comparar(Carta a, Carta b, Optional<Carta> vira) {
        return Integer.compare(forca(a, vira), forca(b, vira));
    }
}
