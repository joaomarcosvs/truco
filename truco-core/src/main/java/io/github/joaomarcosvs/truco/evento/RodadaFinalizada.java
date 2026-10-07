package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.partida.EquipeId;
import java.util.Objects;

/** Uma rodada terminou com vencedora, que marca os pontos dela (RG-VAZA-3). */
public record RodadaFinalizada(int numeroDaRodada, EquipeId vencedora, int pontos) implements Evento {

    public RodadaFinalizada {
        Objects.requireNonNull(vencedora, "vencedora");
    }

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
