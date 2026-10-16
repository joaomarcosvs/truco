package io.github.joaomarcosvs.truco.web;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.evento.Evento;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.MotorDeTruco;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Uma ação aplicada, como um jogador a vê: os eventos que ele pode ver e o estado depois dela. A página mostra os passos
 * um a um.
 *
 * <p>{@code viraAntes} é a vira da rodada em que a ação foi feita, a das cartas jogadas nos eventos. Ela é outra que a
 * do estado quando a ação encerra a rodada, porque o mesmo passo já distribui a seguinte.
 */
record Passo(List<Evento> eventos, EstadoDaPartida estado, Optional<Carta> viraAntes) {

    static final MotorDeTruco MOTOR = MotorDeTruco.novo();

    Passo {
        eventos = List.copyOf(eventos);
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(viraAntes, "viraAntes");
    }

    /** Um passo sem eventos para o jogador, no estado dado. */
    static Passo semEventos(EstadoDaPartida estado) {
        return new Passo(List.of(), estado, estado.rodada().vira());
    }

    /** Os eventos que o jogador pode ver da ação feita em {@code antes}, e o estado depois dela. */
    static Passo visto(JogadorId jogador, List<Evento> eventos, EstadoDaPartida antes, EstadoDaPartida estado) {
        return new Passo(
                eventos.stream().filter(evento -> evento.visivelPara(jogador)).toList(),
                estado,
                antes.rodada().vira());
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
                estado,
                estado.rodada().vira());
    }

    VisaoDoJogador visaoDe(JogadorId jogador) {
        return MOTOR.visaoDe(estado, jogador);
    }
}
