package io.github.joaomarcosvs.truco.regras.paulista;

import io.github.joaomarcosvs.truco.regras.EscadaDeApostas;

/** Escada de apostas do Truco Paulista: a rodada começa valendo 1 ponto (RG-PARTIDA-3). */
final class EscadaDeApostasPaulista implements EscadaDeApostas {

    @Override
    public int valorInicial() {
        return 1;
    }
}
