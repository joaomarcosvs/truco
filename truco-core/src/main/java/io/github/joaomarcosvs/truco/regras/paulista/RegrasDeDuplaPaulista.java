package io.github.joaomarcosvs.truco.regras.paulista;

import io.github.joaomarcosvs.truco.regras.RegrasDeDupla;

/** Duplas do Truco Paulista: 1x1 ou 2x2, e as cartas do parceiro só no aumento e na Rodada de Onze (RG-2X2-*). */
final class RegrasDeDuplaPaulista implements RegrasDeDupla {

    @Override
    public boolean aceitaJogadoresPorEquipe(int jogadores) {
        return jogadores == 1 || jogadores == 2; // RG-ESC-1
    }

    @Override
    public boolean podeVerCartasAoReceberAumento() {
        return true; // RG-2X2-9 e RG-2X2-10
    }

    @Override
    public boolean parceirosSeVeemNaRodadaDeOnze() {
        return true; // RG-2X2-11
    }
}
