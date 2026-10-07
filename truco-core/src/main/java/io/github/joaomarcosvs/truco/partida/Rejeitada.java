package io.github.joaomarcosvs.truco.partida;

import java.util.Objects;

/** A ação foi recusada e o estado não mudou. */
public record Rejeitada(MotivoDeRejeicao motivo) implements Resultado {

    public Rejeitada {
        Objects.requireNonNull(motivo, "motivo");
    }
}
