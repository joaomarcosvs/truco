package io.github.joaomarcosvs.truco.partida;

import static java.util.stream.Collectors.toUnmodifiableMap;

import io.github.joaomarcosvs.truco.carta.Carta;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A rodada em andamento (RG-PARTIDA-2): a aposta, a vira, as mãos, o baralho restante, as vazas encerradas, as jogadas
 * da vaza em andamento e a fase. Contém informação oculta, então só o servidor a enxerga.
 */
public record Rodada(
        Aposta aposta,
        Optional<Carta> vira,
        Map<JogadorId, List<Carta>> maos,
        List<Carta> baralhoRestante,
        List<Vaza> vazas,
        List<Jogada> vazaAtual,
        FaseDaRodada fase) {

    public Rodada {
        Objects.requireNonNull(aposta, "aposta");
        Objects.requireNonNull(vira, "vira");
        maos = maos.entrySet().stream()
                .collect(toUnmodifiableMap(Map.Entry::getKey, entrada -> List.copyOf(entrada.getValue())));
        baralhoRestante = List.copyOf(baralhoRestante);
        vazas = List.copyOf(vazas);
        vazaAtual = List.copyOf(vazaAtual);
        Objects.requireNonNull(fase, "fase");
    }

    /** As cartas na mão do jogador, na ordem das posições usadas por {@code indiceNaMao}. */
    public List<Carta> maoDe(JogadorId jogador) {
        List<Carta> mao = maos.get(jogador);
        if (mao == null) {
            throw new IllegalArgumentException("Jogador fora da rodada: " + jogador);
        }
        return mao;
    }

    /** Número da vaza em andamento, a partir de 1. */
    public int numeroDaVazaAtual() {
        return vazas.size() + 1;
    }
}
