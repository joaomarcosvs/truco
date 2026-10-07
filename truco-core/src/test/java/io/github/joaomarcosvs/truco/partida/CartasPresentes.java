package io.github.joaomarcosvs.truco.partida;

import io.github.joaomarcosvs.truco.carta.Carta;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Onde as cartas aparecem: na rodada, para a conservação, e em visões e eventos, para a informação oculta. */
final class CartasPresentes {

    private CartasPresentes() {}

    /** Todas as cartas da rodada: mãos, baralho restante, vira e cartas jogadas. */
    static List<Carta> naRodada(Rodada rodada) {
        List<Carta> cartas = new ArrayList<>();
        rodada.maos().values().forEach(cartas::addAll);
        cartas.addAll(rodada.baralhoRestante());
        rodada.vira().ifPresent(cartas::add);
        rodada.vazas().forEach(vaza -> vaza.jogadas().forEach(jogada -> cartas.add(jogada.carta())));
        rodada.vazaAtual().forEach(jogada -> cartas.add(jogada.carta()));
        return cartas;
    }

    /** As cartas que o jogador não pode ver: as mãos dos outros e o baralho restante (RG-VIS-2). */
    static Set<Carta> ocultasPara(JogadorId jogador, Rodada rodada) {
        Set<Carta> ocultas = new HashSet<>(rodada.baralhoRestante());
        rodada.maos().forEach((dono, mao) -> {
            if (!dono.equals(jogador)) {
                ocultas.addAll(mao);
            }
        });
        return ocultas;
    }

    /**
     * Se a carta aparece em qualquer lugar do objeto (visão, evento, lista de eventos). Procura a notação da carta no
     * {@code toString}: os símbolos de naipe só aparecem em cartas, e nenhuma notação contém outra, então a busca vale
     * para qualquer campo, inclusive os que forem criados depois.
     */
    static boolean mostra(Object objeto, Carta carta) {
        return objeto.toString().contains(carta.toString());
    }
}
