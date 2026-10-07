package io.github.joaomarcosvs.truco.partida;

import io.github.joaomarcosvs.truco.carta.Carta;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Onde as cartas aparecem: na rodada, para a conservação, e em visões e eventos, para a informação oculta. */
final class CartasPresentes {

    private CartasPresentes() {}

    /** Todas as cartas da rodada: mãos, baralho restante, vira, descartadas e cartas jogadas. */
    static List<Carta> naRodada(Rodada rodada) {
        List<Carta> cartas = new ArrayList<>();
        rodada.maos().values().forEach(cartas::addAll);
        cartas.addAll(rodada.baralhoRestante());
        rodada.vira().ifPresent(cartas::add);
        cartas.addAll(rodada.descarte().descartadas());
        rodada.vazas().forEach(vaza -> vaza.jogadas().forEach(jogada -> cartas.add(jogada.carta())));
        rodada.vazaAtual().forEach(jogada -> cartas.add(jogada.carta()));
        return cartas;
    }

    /**
     * As cartas que o jogador não pode ver: as mãos dos outros, o baralho restante (RG-VIS-2), as cartas que os outros
     * jogaram encobertas (RG-ENC-3) e, na Rodada Escurinho, a própria mão (RG-VIS-3).
     */
    static Set<Carta> ocultasPara(JogadorId jogador, Rodada rodada) {
        Set<Carta> ocultas = new HashSet<>(rodada.baralhoRestante());
        boolean escurinho = rodada.tipo() instanceof TipoDeRodada.Escurinho;
        rodada.maos().forEach((dono, mao) -> {
            if (!dono.equals(jogador) || escurinho) {
                ocultas.addAll(mao);
            }
        });
        Stream.concat(rodada.vazas().stream().flatMap(vaza -> vaza.jogadas().stream()), rodada.vazaAtual().stream())
                .filter(jogada -> jogada.encoberta() && !jogada.jogador().equals(jogador))
                .forEach(jogada -> ocultas.add(jogada.carta()));
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
