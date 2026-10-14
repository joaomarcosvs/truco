package io.github.joaomarcosvs.truco.bots;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.partida.Aplicada;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.MotorDeTruco;
import io.github.joaomarcosvs.truco.partida.Resultado;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Conduz uma partida local até o fim. A cada passo, o primeiro jogador da mesa que tem ações legais recebe a própria
 * visão e as ações legais, a sua {@link Estrategia} escolhe uma, e o motor a aplica. No descarte, em que todos
 * respondem (RG-DESC-10), os jogadores são consultados um de cada vez, na ordem da mesa.
 */
public final class Arbitro {

    private final MotorDeTruco motor;
    private final Map<JogadorId, Estrategia> estrategias;

    /** Árbitro com uma estratégia para cada jogador. */
    public Arbitro(MotorDeTruco motor, Map<JogadorId, Estrategia> estrategias) {
        this.motor = Objects.requireNonNull(motor, "motor");
        this.estrategias = Map.copyOf(estrategias);
    }

    /**
     * Joga a partida a partir do estado dado até que ela acabe, avisando o observador a cada ação aplicada.
     *
     * @return o estado final, na fase {@link FaseDaRodada.PartidaFinalizada}
     * @throws IllegalArgumentException se algum jogador da partida não tem estratégia
     * @throws IllegalStateException se uma estratégia escolher uma ação fora das legais
     */
    public EstadoDaPartida jogar(EstadoDaPartida inicio, ObservadorDaPartida observador) {
        List<JogadorId> mesa = inicio.configuracao().jogadoresNaOrdemDaMesa();
        for (JogadorId jogador : mesa) {
            if (!estrategias.containsKey(jogador)) {
                throw new IllegalArgumentException("Jogador sem estratégia: " + jogador);
            }
        }
        EstadoDaPartida estado = inicio;
        while (!(estado.rodada().fase() instanceof FaseDaRodada.PartidaFinalizada)) {
            estado = passo(estado, mesa, observador);
        }
        return estado;
    }

    private EstadoDaPartida passo(EstadoDaPartida estado, List<JogadorId> mesa, ObservadorDaPartida observador) {
        JogadorId jogador = mesa.stream()
                .filter(candidato -> !motor.acoesLegais(estado, candidato).isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("A partida não acabou, mas ninguém pode agir"));
        List<Acao> legais = motor.acoesLegais(estado, jogador);
        Acao acao = estrategias.get(jogador).decidir(motor.visaoDe(estado, jogador), legais);
        if (!legais.contains(acao)) {
            throw new IllegalStateException(
                    "A estratégia de " + jogador + " escolheu " + acao + ", fora das ações legais " + legais);
        }
        Resultado resultado = motor.aplicar(estado, jogador, acao);
        if (!(resultado instanceof Aplicada aplicada)) {
            throw new IllegalStateException("O motor rejeitou uma ação legal de " + jogador + ": " + resultado);
        }
        observador.aoAplicar(estado, jogador, acao, aplicada);
        return aplicada.novoEstado();
    }
}
