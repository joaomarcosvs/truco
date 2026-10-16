package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.util.List;
import java.util.Objects;

/**
 * As cartas que o jogador viu na mão do parceiro: ao responder a um aumento (RG-2X2-10) ou na decisão da Rodada de
 * Onze (RG-2X2-11). Só quem viu recebe (RG-2X2-15).
 */
public record CartasDoParceiroVistas(JogadorId jogador, JogadorId parceiro, List<Carta> cartas) implements Evento {

    public CartasDoParceiroVistas {
        Objects.requireNonNull(jogador, "jogador");
        Objects.requireNonNull(parceiro, "parceiro");
        cartas = List.copyOf(cartas);
    }

    @Override
    public Visibilidade visibilidade() {
        return new Visibilidade.Privado(jogador);
    }
}
