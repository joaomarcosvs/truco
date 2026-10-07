package io.github.joaomarcosvs.truco.regras.paulista;

import io.github.joaomarcosvs.truco.regras.PontuacaoDaPartida;

/** Pontuação do Truco Paulista: vence quem chegar a 12 pontos ou mais (RG-PARTIDA-1, RG-FIM-1). */
final class PontuacaoDaPartidaPaulista implements PontuacaoDaPartida {

    @Override
    public int pontosParaVencer() {
        return 12;
    }
}
