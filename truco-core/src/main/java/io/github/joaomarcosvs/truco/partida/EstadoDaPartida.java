package io.github.joaomarcosvs.truco.partida;

import java.util.Objects;

/**
 * Estado completo da partida. Contém informação oculta (as mãos e a ordem do baralho), então fica só no servidor; cada
 * jogador recebe a sua {@link io.github.joaomarcosvs.truco.visao.VisaoDoJogador}.
 */
public record EstadoDaPartida(
        ConfiguracaoDaPartida configuracao, Placar placar, int numeroDaRodada, JogadorId carteador, Rodada rodada) {

    public EstadoDaPartida {
        Objects.requireNonNull(configuracao, "configuracao");
        Objects.requireNonNull(placar, "placar");
        Objects.requireNonNull(carteador, "carteador");
        Objects.requireNonNull(rodada, "rodada");
        if (numeroDaRodada < 1) {
            throw new IllegalArgumentException("As rodadas são numeradas a partir de 1: " + numeroDaRodada);
        }
    }
}
