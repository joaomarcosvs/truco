package io.github.joaomarcosvs.truco.regras;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.partida.DesfechoDaRodada;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.Jogada;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza;
import io.github.joaomarcosvs.truco.partida.Vaza;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Como as vazas são jogadas e decididas (RG-VAZA-* e RG-EMP-* no Truco Paulista). */
public interface RegrasDeVaza {

    /** Quantas cartas cada jogador recebe na distribuição (RG-PARTIDA-2 no Truco Paulista). */
    int cartasPorJogador();

    /**
     * Resultado de uma vaza completa, com as jogadas na ordem em que foram feitas. {@code equipeDe} informa a equipe de
     * cada jogador.
     */
    ResultadoDaVaza resultado(List<Jogada> jogadas, Optional<Carta> vira, Function<JogadorId, EquipeId> equipeDe);

    /** Quem abre a vaza seguinte a uma vaza encerrada. */
    JogadorId abreAProxima(Vaza vaza);

    /**
     * Desfecho da rodada pelas vazas encerradas, em ordem; vazio enquanto a rodada não está decidida. A rodada termina
     * na primeira vaza que a decide.
     */
    Optional<DesfechoDaRodada> desfecho(List<Vaza> vazas);
}
