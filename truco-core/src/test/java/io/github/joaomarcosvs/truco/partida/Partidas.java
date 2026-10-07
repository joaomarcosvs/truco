package io.github.joaomarcosvs.truco.partida;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
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

    /** Todos recusam o descarte até a fase acabar, e a rodada fica pronta para a 1ª vaza. */
    static EstadoDaPartida passarDescarte(EstadoDaPartida estado) {
        while (estado.rodada().fase() instanceof FaseDaRodada.AguardandoDescarte) {
            estado = aplicarAceita(estado, daVez(estado), new RecusarDescarte()).novoEstado();
        }
        return estado;
    }

    /** O jogador da vez faz a primeira das suas ações legais. */
    static Aplicada primeiraAcaoLegal(EstadoDaPartida estado) {
        JogadorId jogador = daVez(estado);
        return aplicarAceita(estado, jogador, MOTOR.acoesLegais(estado, jogador).getFirst());
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
