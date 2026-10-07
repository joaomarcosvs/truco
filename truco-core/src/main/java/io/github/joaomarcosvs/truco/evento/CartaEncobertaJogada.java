package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.util.Objects;

/** O jogador jogou uma carta encoberta na vaza. Todos sabem que foi encoberta, mas ninguém vê qual é (RG-ENC-3). */
public record CartaEncobertaJogada(JogadorId jogador, int numeroDaVaza) implements Evento {

    public CartaEncobertaJogada {
        Objects.requireNonNull(jogador, "jogador");
    }

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
