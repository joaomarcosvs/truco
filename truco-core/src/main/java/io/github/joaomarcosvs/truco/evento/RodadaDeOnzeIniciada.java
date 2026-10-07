package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.partida.EquipeId;
import java.util.Objects;

/** A rodada que começou é uma Rodada de Onze: a equipe com 11 vai decidir se joga ou corre (RG-ONZE-1). */
public record RodadaDeOnzeIniciada(EquipeId equipeComOnze) implements Evento {

    public RodadaDeOnzeIniciada {
        Objects.requireNonNull(equipeComOnze, "equipeComOnze");
    }

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
