package io.github.joaomarcosvs.truco.partida;

import io.github.joaomarcosvs.truco.carta.Carta;
import java.util.Objects;

/**
 * Uma carta jogada por um jogador numa vaza, aberta ou encoberta (RG-VAZA-1, RG-ENC-1). Mesmo encoberta, a carta fica
 * registrada aqui, no estado do servidor; quem não a jogou nunca a vê (RG-ENC-3).
 */
public record Jogada(JogadorId jogador, Carta carta, boolean encoberta) {

    public Jogada {
        Objects.requireNonNull(jogador, "jogador");
        Objects.requireNonNull(carta, "carta");
    }

    /** Carta jogada aberta. */
    public static Jogada aberta(JogadorId jogador, Carta carta) {
        return new Jogada(jogador, carta, false);
    }

    /** Carta jogada encoberta, virada para baixo. */
    public static Jogada encoberta(JogadorId jogador, Carta carta) {
        return new Jogada(jogador, carta, true);
    }
}
