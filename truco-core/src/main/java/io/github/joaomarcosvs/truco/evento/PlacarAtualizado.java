package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.partida.Placar;
import java.util.Objects;

/** O placar mudou. */
public record PlacarAtualizado(Placar placar) implements Evento {

    public PlacarAtualizado {
        Objects.requireNonNull(placar, "placar");
    }

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
