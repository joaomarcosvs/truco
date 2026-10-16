package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.util.Objects;

/**
 * O jogador quer ver as cartas do parceiro para responder ao aumento. É público, como seria falado na mesa
 * (RG-2X2-9).
 */
public record QuisVerAsCartas(JogadorId jogador) implements Evento {

    public QuisVerAsCartas {
        Objects.requireNonNull(jogador, "jogador");
    }

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
