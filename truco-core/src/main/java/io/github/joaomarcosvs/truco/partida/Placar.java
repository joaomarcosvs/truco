package io.github.joaomarcosvs.truco.partida;

import static java.util.stream.Collectors.toMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pontos de cada equipe na partida (RG-PARTIDA-1). */
public record Placar(Map<EquipeId, Integer> pontos) {

    public Placar {
        pontos = Map.copyOf(pontos);
        if (pontos.values().stream().anyMatch(valor -> valor < 0)) {
            throw new IllegalArgumentException("Pontos não podem ser negativos: " + pontos);
        }
    }

    /** Placar zerado para as equipes. */
    public static Placar zerado(List<Equipe> equipes) {
        return new Placar(equipes.stream().collect(toMap(Equipe::id, equipe -> 0)));
    }

    /** Pontos da equipe. */
    public int pontosDe(EquipeId equipe) {
        Integer valor = pontos.get(equipe);
        if (valor == null) {
            throw new IllegalArgumentException("Equipe fora do placar: " + equipe);
        }
        return valor;
    }

    /** Novo placar com os pontos somados aos da equipe. */
    public Placar somando(EquipeId equipe, int ganhos) {
        Map<EquipeId, Integer> novos = new HashMap<>(pontos);
        novos.put(equipe, pontosDe(equipe) + ganhos);
        return new Placar(novos);
    }
}
