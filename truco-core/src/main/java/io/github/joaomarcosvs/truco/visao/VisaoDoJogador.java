package io.github.joaomarcosvs.truco.visao;

import static java.util.stream.Collectors.toUnmodifiableMap;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.partida.Aposta;
import io.github.joaomarcosvs.truco.partida.Equipe;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.partida.JogadorId;
import io.github.joaomarcosvs.truco.partida.Placar;
import io.github.joaomarcosvs.truco.partida.TipoDeRodada;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * O que um jogador pode ver da partida (RG-VIS-1, RG-VIS-2): a própria mão, a vira, as cartas descartadas e jogadas, o
 * placar e o andamento da rodada. Nunca as cartas dos outros (a não ser as do parceiro, nos momentos em que a dupla se
 * vê), a ordem do baralho nem quem tem a carta da vez no descarte.
 *
 * @param tipoDaRodada se a rodada é normal, de Onze ou Escurinho
 * @param aposta quanto a rodada vale, quem pode aumentar e o pedido de aumento pendente
 * @param descartadas as cartas descartadas na rodada, que são públicas (RG-DESC-8)
 * @param cartaDaVezNoDescarte a carta da vez, durante a fase de descarte
 * @param mao a própria mão; vazia na Rodada Escurinho, em que o jogador não a vê (RG-VIS-3)
 * @param maosDosParceiros as mãos dos parceiros que o jogador vê agora: só no 2x2, quando a dupla responde a um
 *     aumento depois de todos quererem se ver (RG-2X2-10) e na decisão da Rodada de Onze (RG-2X2-11); vazio no resto
 * @param cartasNaMao quantas cartas cada jogador tem na mão
 * @param vazas as vazas encerradas da rodada, sem as cartas encobertas dos outros
 * @param vazaAtual as jogadas da vaza em andamento, sem as cartas encobertas dos outros
 * @param vezDe de quem se espera a próxima ação, quando isso é público; no descarte, todos decidem
 * @param vencedoraDaPartida a equipe que venceu, quando a partida acabou
 */
public record VisaoDoJogador(
        JogadorId jogador,
        List<JogadorId> jogadoresNaOrdemDaMesa,
        List<Equipe> equipes,
        Placar placar,
        int numeroDaRodada,
        JogadorId carteador,
        TipoDeRodada tipoDaRodada,
        Aposta aposta,
        Optional<Carta> vira,
        List<Carta> descartadas,
        Optional<Carta> cartaDaVezNoDescarte,
        List<Carta> mao,
        Map<JogadorId, List<Carta>> maosDosParceiros,
        Map<JogadorId, Integer> cartasNaMao,
        List<VazaVisivel> vazas,
        List<JogadaVisivel> vazaAtual,
        Optional<JogadorId> vezDe,
        Optional<EquipeId> vencedoraDaPartida) {

    public VisaoDoJogador {
        Objects.requireNonNull(jogador, "jogador");
        jogadoresNaOrdemDaMesa = List.copyOf(jogadoresNaOrdemDaMesa);
        equipes = List.copyOf(equipes);
        Objects.requireNonNull(placar, "placar");
        Objects.requireNonNull(carteador, "carteador");
        Objects.requireNonNull(tipoDaRodada, "tipoDaRodada");
        Objects.requireNonNull(aposta, "aposta");
        Objects.requireNonNull(vira, "vira");
        descartadas = List.copyOf(descartadas);
        Objects.requireNonNull(cartaDaVezNoDescarte, "cartaDaVezNoDescarte");
        mao = List.copyOf(mao);
        maosDosParceiros = maosDosParceiros.entrySet().stream()
                .collect(toUnmodifiableMap(Map.Entry::getKey, entrada -> List.copyOf(entrada.getValue())));
        cartasNaMao = Map.copyOf(cartasNaMao);
        vazas = List.copyOf(vazas);
        vazaAtual = List.copyOf(vazaAtual);
        Objects.requireNonNull(vezDe, "vezDe");
        Objects.requireNonNull(vencedoraDaPartida, "vencedoraDaPartida");
    }
}
