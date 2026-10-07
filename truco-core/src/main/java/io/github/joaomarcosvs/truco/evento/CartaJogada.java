package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.util.Objects;

/** O jogador jogou uma carta aberta na vaza; todos veem a carta (RG-VAZA-1, RG-VIS-1). */
public record CartaJogada(JogadorId jogador, Carta carta, int numeroDaVaza) implements Evento {

    public CartaJogada {
        Objects.requireNonNull(jogador, "jogador");
        Objects.requireNonNull(carta, "carta");
    }

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
