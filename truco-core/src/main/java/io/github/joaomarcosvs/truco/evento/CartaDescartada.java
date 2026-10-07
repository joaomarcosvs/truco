package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.util.Objects;

/** O jogador descartou a carta, que todos veem (RG-DESC-8). */
public record CartaDescartada(JogadorId jogador, Carta carta) implements Evento {

    public CartaDescartada {
        Objects.requireNonNull(jogador, "jogador");
        Objects.requireNonNull(carta, "carta");
    }

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
