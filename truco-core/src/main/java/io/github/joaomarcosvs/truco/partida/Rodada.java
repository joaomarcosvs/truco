package io.github.joaomarcosvs.truco.partida;

import static java.util.stream.Collectors.toUnmodifiableMap;

import io.github.joaomarcosvs.truco.carta.Carta;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A rodada em andamento (RG-PARTIDA-2): quanto vale, a vira, as mãos, o baralho restante, as vazas encerradas, as
 * jogadas da vaza em andamento e a fase. Contém informação oculta, então só o servidor a enxerga.
 */
public record Rodada(
        int valor,
        Optional<Carta> vira,
        Map<JogadorId, List<Carta>> maos,
        List<Carta> baralhoRestante,
        List<Vaza> vazas,
        List<Jogada> vazaAtual,
        FaseDaRodada fase) {

    public Rodada {
        if (valor < 1) {
            throw new IllegalArgumentException("A rodada vale pelo menos 1 ponto: " + valor);
        }
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
