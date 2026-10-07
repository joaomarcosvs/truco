package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza;
import java.util.Objects;

/** Uma vaza terminou, com vencedor ou empatada (RG-VAZA-2, RG-EMP-*). */
public record VazaFinalizada(int numeroDaVaza, ResultadoDaVaza resultado) implements Evento {

    public VazaFinalizada {
        Objects.requireNonNull(resultado, "resultado");
    }

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
