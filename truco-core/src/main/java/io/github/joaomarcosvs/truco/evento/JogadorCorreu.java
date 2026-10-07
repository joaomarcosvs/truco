package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.util.Objects;

/** O jogador correu e desistiu da rodada (RG-AUM-3). */
public record JogadorCorreu(JogadorId jogador) implements Evento {

    public JogadorCorreu {
        Objects.requireNonNull(jogador, "jogador");
    }

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
