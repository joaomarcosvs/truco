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
     * As cartas que o jogador não pode ver: as mãos dos outros (menos as dos parceiros nos momentos em que a dupla se
     * vê), o baralho restante (RG-VIS-2), as cartas que os outros jogaram encobertas (RG-ENC-3, RG-2X2-15) e, na Rodada
     * Escurinho, a própria mão (RG-VIS-3).
     */
    static Set<Carta> ocultasPara(JogadorId jogador, EstadoDaPartida estado) {
        Rodada rodada = estado.rodada();
        Set<Carta> ocultas = new HashSet<>(rodada.baralhoRestante());
        boolean escurinho = rodada.tipo() instanceof TipoDeRodada.Escurinho;
        Set<JogadorId> parceirosVisiveis = parceirosVisiveis(jogador, estado);
        rodada.maos().forEach((dono, mao) -> {
            boolean propria = dono.equals(jogador);
            if ((!propria && !parceirosVisiveis.contains(dono)) || escurinho) {
                ocultas.addAll(mao);
            }
        });
        Stream.concat(rodada.vazas().stream().flatMap(vaza -> vaza.jogadas().stream()), rodada.vazaAtual().stream())
                .filter(jogada -> jogada.encoberta() && !jogada.jogador().equals(jogador))
                .forEach(jogada -> ocultas.add(jogada.carta()));
        return ocultas;
    }

    /**
     * Os parceiros cuja mão o jogador pode ver agora, escrito de novo a partir das regras, para conferir o motor: quando
     * a dupla dele responde a um aumento e todos quiseram ver as cartas (RG-2X2-10), e na decisão da Rodada de Onze, se
     * a dupla é a que tem 11 (RG-2X2-11). No resto, e sempre no 1x1, nenhum (RG-2X2-15).
     */
    static Set<JogadorId> parceirosVisiveis(JogadorId jogador, EstadoDaPartida estado) {
        ConfiguracaoDaPartida configuracao = estado.configuracao();
        List<JogadorId> parceiros = configuracao.parceirosDe(jogador);
        EquipeId equipe = configuracao.equipeDe(jogador);
        boolean seVeem = switch (estado.rodada().fase()) {
            case FaseDaRodada.AguardandoRespostaDeAumento resposta -> {
                List<JogadorId> querem =
                        estado.rodada().aposta().pedidoPendente().orElseThrow().queremVerAsCartas();
                yield configuracao.equipeDe(resposta.respondedor()).equals(equipe)
                        && querem.contains(jogador)
                        && querem.containsAll(parceiros);
            }
            case FaseDaRodada.DecisaoRodadaDeOnze decisao ->
                configuracao.equipeDe(decisao.jogador()).equals(equipe);
            default -> false;
        };
        return seVeem ? Set.copyOf(parceiros) : Set.of();
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
