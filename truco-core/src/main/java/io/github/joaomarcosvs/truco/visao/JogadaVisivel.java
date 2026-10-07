package io.github.joaomarcosvs.truco.visao;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.util.Objects;
import java.util.Optional;

/**
 * Uma jogada como o jogador a vê. A carta de uma jogada encoberta só aparece para quem a jogou; para os outros, fica
 * vazia (RG-ENC-3).
 */
public record JogadaVisivel(JogadorId jogador, Optional<Carta> carta, boolean encoberta) {

    public JogadaVisivel {
        Objects.requireNonNull(jogador, "jogador");
        Objects.requireNonNull(carta, "carta");
    }
}
