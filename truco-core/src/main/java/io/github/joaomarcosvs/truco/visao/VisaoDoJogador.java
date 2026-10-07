package io.github.joaomarcosvs.truco.visao;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.partida.Aposta;
import io.github.joaomarcosvs.truco.partida.Equipe;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.Placar;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * O que um jogador pode ver da partida (RG-VIS-1, RG-VIS-2): a própria mão, a vira, as cartas jogadas, o placar e o
 * andamento da rodada. Nunca as cartas dos outros nem a ordem do baralho.
 *
 * @param aposta quanto a rodada vale, quem pode aumentar e o pedido de aumento pendente
 * @param cartasNaMao quantas cartas cada jogador tem na mão
 * @param vazas as vazas encerradas da rodada, sem as cartas encobertas dos outros
 * @param vazaAtual as jogadas da vaza em andamento, sem as cartas encobertas dos outros
 * @param vezDe de quem se espera a próxima ação, quando isso é público
 */
public record VisaoDoJogador(
        JogadorId jogador,
        List<JogadorId> jogadoresNaOrdemDaMesa,
        List<Equipe> equipes,
        Placar placar,
        int numeroDaRodada,
        JogadorId carteador,
        Aposta aposta,
        Optional<Carta> vira,
        List<Carta> mao,
        Map<JogadorId, Integer> cartasNaMao,
        List<VazaVisivel> vazas,
        List<JogadaVisivel> vazaAtual,
        Optional<JogadorId> vezDe) {

    public VisaoDoJogador {
        Objects.requireNonNull(jogador, "jogador");
        jogadoresNaOrdemDaMesa = List.copyOf(jogadoresNaOrdemDaMesa);
        equipes = List.copyOf(equipes);
        Objects.requireNonNull(placar, "placar");
        Objects.requireNonNull(carteador, "carteador");
        Objects.requireNonNull(aposta, "aposta");
        Objects.requireNonNull(vira, "vira");
        mao = List.copyOf(mao);
        cartasNaMao = Map.copyOf(cartasNaMao);
        vazas = List.copyOf(vazas);
        vazaAtual = List.copyOf(vazaAtual);
        Objects.requireNonNull(vezDe, "vezDe");
    }
}
