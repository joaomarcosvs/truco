package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.util.Objects;

/** A carta que o jogador comprou no lugar da descartada. Só ele vê (RG-DESC-8, RG-VIS-4). */
public record CartaRecebidaPorDescarte(JogadorId jogador, Carta carta) implements Evento {

    public CartaRecebidaPorDescarte {
        Objects.requireNonNull(jogador, "jogador");
        Objects.requireNonNull(carta, "carta");
    }

    @Override
    public Visibilidade visibilidade() {
        return new Visibilidade.Privado(jogador);
    }
}
