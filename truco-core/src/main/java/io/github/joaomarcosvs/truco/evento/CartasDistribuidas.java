package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.util.List;
import java.util.Objects;

/** As cartas que o jogador recebeu na distribuição. Só ele vê (RG-VIS-2). */
public record CartasDistribuidas(JogadorId jogador, List<Carta> cartas) implements Evento {

    public CartasDistribuidas {
        Objects.requireNonNull(jogador, "jogador");
        cartas = List.copyOf(cartas);
    }

    @Override
    public Visibilidade visibilidade() {
        return new Visibilidade.Privado(jogador);
    }
}
