package io.github.joaomarcosvs.truco.web;

import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.MotorDeTruco;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.List;

/**
 * Uma ação aplicada, como um jogador a vê: os eventos que ele pode ver e o estado depois dela. A página mostra os passos
 * um a um.
 */
record Passo(List<Evento> eventos, EstadoDaPartida estado) {

    static final MotorDeTruco MOTOR = MotorDeTruco.novo();

    Passo {
        eventos = List.copyOf(eventos);
    }

    /** Os eventos que o jogador pode ver e o estado depois deles. */
    static Passo visto(JogadorId jogador, List<Evento> eventos, EstadoDaPartida estado) {
        return new Passo(
                eventos.stream().filter(evento -> evento.visivelPara(jogador)).toList(), estado);
    }

    /** A 1ª rodada já vem distribuída no estado inicial, sem eventos: ela é contada como as seguintes. */
    static Passo inicio(JogadorId jogador, EstadoDaPartida estado) {
        VisaoDoJogador visao = MOTOR.visaoDe(estado, jogador);
        return new Passo(
                List.of(
                        new RodadaIniciada(
                                visao.numeroDaRodada(),
                                visao.carteador(),
                                visao.aposta().valor(),
                                visao.vira()),
                        new CartasDistribuidas(jogador, visao.mao())),
                estado);
    }

    VisaoDoJogador visaoDe(JogadorId jogador) {
        return MOTOR.visaoDe(estado, jogador);
    }
}
