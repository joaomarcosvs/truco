package io.github.joaomarcosvs.truco.bots;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.partida.Aplicada;
import io.github.joaomarcosvs.truco.partida.EstadoDaPartida;
import io.github.joaomarcosvs.truco.partida.JogadorId;

/**
 * Acompanha uma partida conduzida pelo {@link Arbitro}. Recebe o estado completo, com informação oculta: quem mostra
 * algo a um jogador precisa filtrar os eventos com {@code Evento.visivelPara}.
 */
@FunctionalInterface
public interface ObservadorDaPartida {

    /** Chamado depois de cada ação aplicada, com o estado de antes, quem agiu, a ação e o resultado. */
    void aoAplicar(EstadoDaPartida antes, JogadorId jogador, Acao acao, Aplicada aplicada);
}
