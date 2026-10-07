package io.github.joaomarcosvs.truco.partida;

import io.github.joaomarcosvs.truco.carta.Carta;
import java.util.Objects;

/** Uma carta jogada por um jogador numa vaza (RG-VAZA-1). */
public record Jogada(JogadorId jogador, Carta carta) {

    public Jogada {
        Objects.requireNonNull(jogador, "jogador");
        Objects.requireNonNull(carta, "carta");
    }
}
