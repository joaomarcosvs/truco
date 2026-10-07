package io.github.joaomarcosvs.truco.partida;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.List;

/**
 * O motor do jogo, puro e determinístico: as regras vêm da variante da configuração, e a mesma seed com as mesmas ações
 * gera a mesma partida. Ação ilegal não lança exceção; volta como {@link Rejeitada}.
 */
public interface MotorDeTruco {

    /** Começa a partida, já com a 1ª rodada distribuída. */
    EstadoDaPartida novaPartida(ConfiguracaoDaPartida configuracao);

    /** As ações que o jogador pode fazer agora; vazia quando não é a vez dele. */
    List<Acao> acoesLegais(EstadoDaPartida estado, JogadorId jogador);

    /** Aplica a ação do jogador. Toda ação fora de {@link #acoesLegais} é rejeitada. */
    Resultado aplicar(EstadoDaPartida estado, JogadorId jogador, Acao acao);

    /** O que o jogador pode ver da partida (RG-VIS-1, RG-VIS-2). */
    VisaoDoJogador visaoDe(EstadoDaPartida estado, JogadorId jogador);

    /** O motor genérico, que serve a qualquer variante. */
    static MotorDeTruco novo() {
        return new MotorGenerico();
    }
}
