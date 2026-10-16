package io.github.joaomarcosvs.truco.bots;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.VerCartasDoParceiro;
import io.github.joaomarcosvs.truco.partida.Aplicada;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.MotorDeTruco;
import io.github.joaomarcosvs.truco.partida.PedidoDeAumento;
import io.github.joaomarcosvs.truco.partida.Resultado;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Conduz uma partida local até o fim. A cada passo, o primeiro jogador da mesa que tem a vez (alguma ação legal que não
 * seja opcional) recebe a própria visão e as ações legais, a sua {@link Estrategia} escolhe uma, e o motor a aplica. No
 * descarte, em que todos respondem (RG-DESC-10), os jogadores são consultados um de cada vez, na ordem da mesa.
 *
 * <p>Antes de quem tem a vez, quem só tem ações opcionais é consultado ({@link Estrategia#decidirOpcional}), e pode
 * não fazer nada: no 2x2, o parceiro de quem recebeu um pedido de aumento decide se quer ver as cartas (RG-2X2-9). Ele
 * é consultado uma vez a cada mudança do pedido, como quando o parceiro passa a querer ver as cartas também.
 */
public final class Arbitro {

    private final MotorDeTruco motor;
    private final Map<JogadorId, Estrategia> estrategias;

    /** Árbitro com uma estratégia para cada jogador. */
    public Arbitro(MotorDeTruco motor, Map<JogadorId, Estrategia> estrategias) {
        this.motor = Objects.requireNonNull(motor, "motor");
        this.estrategias = Map.copyOf(estrategias);
    }

    /** Um jogador consultado sobre as ações opcionais, num momento da partida: a rodada e o pedido pendente. */
    private record Consulta(JogadorId jogador, int rodada, Optional<PedidoDeAumento> pedido) {}

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
        Set<Consulta> consultas = new HashSet<>();
        EstadoDaPartida estado = inicio;
        while (!(estado.rodada().fase() instanceof FaseDaRodada.PartidaFinalizada)) {
            estado = passo(estado, mesa, consultas, observador);
        }
        return estado;
    }

    private EstadoDaPartida passo(
            EstadoDaPartida estado, List<JogadorId> mesa, Set<Consulta> consultas, ObservadorDaPartida observador) {
        for (JogadorId candidato : mesa) {
            List<Acao> legais = motor.acoesLegais(estado, candidato);
            boolean soOpcionais = !legais.isEmpty() && legais.stream().allMatch(Arbitro::opcional);
            Consulta consulta = new Consulta(
                    candidato, estado.numeroDaRodada(), estado.rodada().aposta().pedidoPendente());
            if (soOpcionais && consultas.add(consulta)) {
                Optional<Acao> escolha =
                        estrategias.get(candidato).decidirOpcional(motor.visaoDe(estado, candidato), legais);
                if (escolha.isPresent()) {
                    return aplicar(estado, candidato, legais, escolha.get(), observador);
                }
            }
        }
        JogadorId jogador = mesa.stream()
                .filter(candidato ->
                        motor.acoesLegais(estado, candidato).stream().anyMatch(acao -> !opcional(acao)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("A partida não acabou, mas ninguém tem a vez"));
        List<Acao> legais = motor.acoesLegais(estado, jogador);
        Acao acao = estrategias.get(jogador).decidir(motor.visaoDe(estado, jogador), legais);
        return aplicar(estado, jogador, legais, acao, observador);
    }

    private EstadoDaPartida aplicar(
            EstadoDaPartida estado, JogadorId jogador, List<Acao> legais, Acao acao, ObservadorDaPartida observador) {
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

    /** Uma ação que o jogador pode deixar de fazer, sem que a partida espere por ela (RG-2X2-9). */
    private static boolean opcional(Acao acao) {
        return acao instanceof VerCartasDoParceiro;
    }
}
