package io.github.joaomarcosvs.truco.partida;

import io.github.joaomarcosvs.truco.regras.VarianteDeRegras;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Como a partida é montada: equipes, variante de regras e seed (RG-ESC-1). Na mesa, os jogadores alternam as equipes,
 * e cada um está à direita do anterior (RG-PARTIDA-4).
 */
public record ConfiguracaoDaPartida(List<Equipe> equipes, VarianteDeRegras variante, long seed) {

    public ConfiguracaoDaPartida {
        equipes = List.copyOf(equipes);
        Objects.requireNonNull(variante, "variante");
        if (equipes.size() < 2) {
            throw new IllegalArgumentException("Uma partida precisa de pelo menos duas equipes");
        }
        if (equipes.stream().map(equipe -> equipe.jogadores().size()).distinct().count() > 1) {
            throw new IllegalArgumentException("Todas as equipes precisam ter o mesmo número de jogadores");
        }
        if (equipes.stream().map(Equipe::id).distinct().count() < equipes.size()) {
            throw new IllegalArgumentException("Há equipes repetidas");
        }
        List<JogadorId> jogadores =
                equipes.stream().flatMap(equipe -> equipe.jogadores().stream()).toList();
        if (jogadores.stream().distinct().count() < jogadores.size()) {
            throw new IllegalArgumentException("Há jogadores repetidos");
        }
    }

    /** Os jogadores na ordem da mesa: as equipes se alternam, e cada jogador está à direita do anterior. */
    public List<JogadorId> jogadoresNaOrdemDaMesa() {
        int porEquipe = equipes.getFirst().jogadores().size();
        List<JogadorId> mesa = new ArrayList<>();
        for (int posicao = 0; posicao < porEquipe; posicao++) {
            for (Equipe equipe : equipes) {
                mesa.add(equipe.jogadores().get(posicao));
            }
        }
        return List.copyOf(mesa);
    }

    /** Os jogadores na ordem da mesa, começando pelo indicado e seguindo para a direita. */
    public List<JogadorId> jogadoresAPartirDe(JogadorId primeiro) {
        List<JogadorId> mesa = jogadoresNaOrdemDaMesa();
        int inicio = mesa.indexOf(primeiro);
        if (inicio < 0) {
            throw new IllegalArgumentException("Jogador fora da partida: " + primeiro);
        }
        List<JogadorId> ordem = new ArrayList<>(mesa.subList(inicio, mesa.size()));
        ordem.addAll(mesa.subList(0, inicio));
        return List.copyOf(ordem);
    }

    /** O jogador à direita: o seguinte na ordem da mesa (RG-PARTIDA-4). */
    public JogadorId aDireitaDe(JogadorId jogador) {
        return jogadoresAPartirDe(jogador).get(1);
    }

    /** Se o jogador está na partida. */
    public boolean participa(JogadorId jogador) {
        return equipes.stream().anyMatch(equipe -> equipe.jogadores().contains(jogador));
    }

    /** A equipe do jogador. */
    public EquipeId equipeDe(JogadorId jogador) {
        return equipes.stream()
                .filter(equipe -> equipe.jogadores().contains(jogador))
                .map(Equipe::id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Jogador fora da partida: " + jogador));
    }
}
