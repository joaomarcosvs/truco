package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.Placar;
import java.util.Objects;

/** A partida acabou: a equipe vencedora chegou aos pontos para vencer (RG-PARTIDA-1, RG-FIM-1). */
public record PartidaFinalizada(EquipeId vencedora, Placar placar) implements Evento {

    public PartidaFinalizada {
        Objects.requireNonNull(vencedora, "vencedora");
        Objects.requireNonNull(placar, "placar");
    }

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
