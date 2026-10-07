package io.github.joaomarcosvs.truco.evento;

/**
 * A rodada que começou é uma Rodada Escurinho: ninguém vê a própria mão, e quem vencer a rodada vence a partida
 * (RG-ESCURINHO-1, RG-VIS-3).
 */
public record RodadaEscurinhoIniciada() implements Evento {

    @Override
    public Visibilidade visibilidade() {
        return Visibilidade.PUBLICO;
    }
}
