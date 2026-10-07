package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.util.Objects;

/** O jogador aceitou o aumento, e a rodada passou a valer o novo valor (RG-AUM-3). */
public record AumentoAceito(JogadorId jogador, int valor) implements Evento {

    public AumentoAceito {
        Objects.requireNonNull(jogador, "jogador");
    }

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
