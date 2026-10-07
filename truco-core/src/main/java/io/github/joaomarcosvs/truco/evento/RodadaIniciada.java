package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.util.Objects;
import java.util.Optional;

/** Uma rodada começou: quem deu as cartas, quanto ela vale e a vira, que todos veem (RG-PARTIDA-2, RG-VIS-1). */
public record RodadaIniciada(int numeroDaRodada, JogadorId carteador, int valor, Optional<Carta> vira)
        implements Evento {

    public RodadaIniciada {
        Objects.requireNonNull(carteador, "carteador");
        Objects.requireNonNull(vira, "vira");
    }

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
