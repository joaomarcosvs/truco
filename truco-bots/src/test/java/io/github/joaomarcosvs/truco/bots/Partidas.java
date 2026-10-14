package io.github.joaomarcosvs.truco.bots;

import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.Equipe;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.MotorDeTruco;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import java.util.List;

/** Jogadores, motor e configuração comuns aos testes dos bots. */
final class Partidas {

    static final JogadorId ANA = new JogadorId("ana");
    static final JogadorId BETO = new JogadorId("beto");
    static final EquipeId EQUIPE_DA_ANA = new EquipeId("ana");
    static final EquipeId EQUIPE_DO_BETO = new EquipeId("beto");
    static final MotorDeTruco MOTOR = MotorDeTruco.novo();

    private Partidas() {}

    /** Partida 1x1 de Truco Paulista entre Ana e Beto. */
    static ConfiguracaoDaPartida configuracao(long seed) {
        return new ConfiguracaoDaPartida(
                List.of(Equipe.individual(ANA), Equipe.individual(BETO)), new TrucoPaulista(), seed);
    }
}
