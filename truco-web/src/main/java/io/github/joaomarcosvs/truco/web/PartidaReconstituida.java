package io.github.joaomarcosvs.truco.web;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.RecusarDescarte;
import io.github.joaomarcosvs.truco.bots.BotAleatorio;
import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.partida.Aplicada;
import io.github.joaomarcosvs.truco.partida.ConfiguracaoDaPartida;
import io.github.joaomarcosvs.truco.partida.Equipe;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.MotorDeTruco;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.ArrayList;
import java.util.List;

/**
 * Refaz uma partida contra o bot a partir da seed e das suas jogadas. O core é determinístico (a mesma seed com as
 * mesmas ações dá a mesma partida, princípio 3) e o bot também tem seed, então o servidor não guarda nada entre um
 * pedido e outro: pode dormir ou reiniciar sem perder o jogo.
 */
final class PartidaReconstituida {

    static final JogadorId VOCE = new JogadorId("voce");
    static final JogadorId BOT = new JogadorId("bot");
    static final MotorDeTruco MOTOR = MotorDeTruco.novo();

    private static final List<Acao> SO_PASSAR = List.of(new RecusarDescarte());

    private PartidaReconstituida() {}

    /** Uma ação aplicada: os eventos que você pode ver e o estado depois dela. */
    record Passo(List<Evento> eventos, EstadoDaPartida estado) {

        Passo {
            eventos = List.copyOf(eventos);
        }

        VisaoDoJogador suaVisao() {
            return MOTOR.visaoDe(estado, VOCE);
        }
    }

    /** O resultado de refazer a partida. */
    sealed interface Reconstituicao {}

    /**
     * A partida refeita. {@code passos} vai da sua última jogada (inclusive) até agora; sem jogadas, começa na
     * distribuição da 1ª rodada. O estado final espera uma decisão sua ou é o fim da partida.
     */
    record Reconstituida(List<Passo> passos) implements Reconstituicao {

        Reconstituida {
            passos = List.copyOf(passos);
        }

        EstadoDaPartida estado() {
            return passos.getLast().estado();
        }
    }

    /** A sua jogada de número {@code indice} (a partir de 0) não era legal quando foi feita, ou veio depois do fim. */
    record JogadaInvalida(int indice, Acao acao) implements Reconstituicao {}

    /** Você contra o bot, no Truco Paulista. */
    static ConfiguracaoDaPartida configuracao(long seed) {
        return new ConfiguracaoDaPartida(
                List.of(Equipe.individual(VOCE), Equipe.individual(BOT)), new TrucoPaulista(), seed);
    }

    static Reconstituicao reconstituir(long seed, List<Acao> suasJogadas) {
        EstadoDaPartida estado = MOTOR.novaPartida(configuracao(seed));
        BotAleatorio bot = new BotAleatorio(seed);
        List<Passo> passos = new ArrayList<>(List.of(inicio(estado)));
        int proxima = 0;
        while (!(estado.rodada().fase() instanceof FaseDaRodada.PartidaFinalizada)) {
            JogadorId jogador = daVez(estado);
            List<Acao> legais = MOTOR.acoesLegais(estado, jogador);
            Acao acao;
            boolean suaJogada = false;
            if (jogador.equals(BOT)) {
                acao = bot.decidir(MOTOR.visaoDe(estado, BOT), legais);
            } else if (legais.equals(SO_PASSAR)) {
                // No descarte todos respondem (RG-DESC-10); sem a carta da vez, não há o que decidir.
                acao = legais.getFirst();
            } else if (proxima < suasJogadas.size()) {
                acao = suasJogadas.get(proxima);
                if (!legais.contains(acao)) {
                    return new JogadaInvalida(proxima, acao);
                }
                proxima++;
                suaJogada = true;
            } else {
                break; // Esperando a sua decisão.
            }
            if (!(MOTOR.aplicar(estado, jogador, acao) instanceof Aplicada aplicada)) {
                throw new IllegalStateException("O motor rejeitou uma ação legal: " + acao);
            }
            if (suaJogada) {
                passos.clear();
            }
            passos.add(new Passo(visiveis(aplicada.eventos()), aplicada.novoEstado()));
            estado = aplicada.novoEstado();
        }
        if (proxima < suasJogadas.size()) {
            return new JogadaInvalida(proxima, suasJogadas.get(proxima));
        }
        return new Reconstituida(passos);
    }

    /** A 1ª rodada já vem distribuída no estado inicial, sem eventos: é contada como as seguintes. */
    private static Passo inicio(EstadoDaPartida estado) {
        VisaoDoJogador visao = MOTOR.visaoDe(estado, VOCE);
        return new Passo(
                List.of(
                        new RodadaIniciada(
                                visao.numeroDaRodada(),
                                visao.carteador(),
                                visao.aposta().valor(),
                                visao.vira()),
                        new CartasDistribuidas(VOCE, visao.mao())),
                estado);
    }

    /** O primeiro jogador da mesa que tem ações legais, como no Arbitro dos bots. */
    private static JogadorId daVez(EstadoDaPartida estado) {
        return estado.configuracao().jogadoresNaOrdemDaMesa().stream()
                .filter(jogador -> !MOTOR.acoesLegais(estado, jogador).isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("A partida não acabou, mas ninguém pode agir"));
    }

    private static List<Evento> visiveis(List<Evento> eventos) {
        return eventos.stream().filter(evento -> evento.visivelPara(VOCE)).toList();
    }
}
