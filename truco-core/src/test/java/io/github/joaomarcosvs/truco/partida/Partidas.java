package io.github.joaomarcosvs.truco.partida;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import java.util.List;

/** Jogadores, motor e configuração comuns aos testes do motor. */
final class Partidas {

    static final JogadorId ANA = new JogadorId("ana");
    static final JogadorId BETO = new JogadorId("beto");
    static final EquipeId EQUIPE_DA_ANA = new EquipeId("ana");
    static final EquipeId EQUIPE_DO_BETO = new EquipeId("beto");
    static final MotorDeTruco MOTOR = MotorDeTruco.novo();

    private Partidas() {}

    /** Partida 1x1 de Truco Paulista. Na mesa, Beto está à direita de Ana, e Ana à direita de Beto. */
    static ConfiguracaoDaPartida configuracao(long seed) {
        return new ConfiguracaoDaPartida(
                List.of(Equipe.individual(ANA), Equipe.individual(BETO)), new TrucoPaulista(), seed);
    }

    /** Quem tem ações legais agora. */
    static JogadorId daVez(EstadoDaPartida estado) {
        return estado.configuracao().jogadoresNaOrdemDaMesa().stream()
                .filter(jogador -> !MOTOR.acoesLegais(estado, jogador).isEmpty())
                .findFirst()
                .orElseThrow();
    }

    /** Aplica uma ação que o motor precisa aceitar. */
    static Aplicada aplicarAceita(EstadoDaPartida estado, JogadorId jogador, Acao acao) {
        Resultado resultado = MOTOR.aplicar(estado, jogador, acao);
        if (resultado instanceof Aplicada aplicada) {
            return aplicada;
        }
        throw new AssertionError(jogador + " deveria conseguir " + acao + ", mas o motor devolveu " + resultado);
    }
}
