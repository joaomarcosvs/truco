package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.partida.EquipeId;
import java.util.Objects;

/**
 * Todos os parceiros da equipe quiseram, e eles viram as cartas uns dos outros. Os adversários sabem que eles olharam,
 * mas não quais são as cartas (RG-2X2-10).
 */
public record DuplaViuAsCartas(EquipeId equipe) implements Evento {

    public DuplaViuAsCartas {
        Objects.requireNonNull(equipe, "equipe");
    }

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
