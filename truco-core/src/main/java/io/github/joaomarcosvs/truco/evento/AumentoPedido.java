package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.util.Objects;

/** O jogador pediu aumento para o nível proposto (RG-AUM-1, RG-AUM-3). */
public record AumentoPedido(JogadorId pedinte, int nivelProposto) implements Evento {

    public AumentoPedido {
        Objects.requireNonNull(pedinte, "pedinte");
    }

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
