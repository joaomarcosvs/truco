package io.github.joaomarcosvs.truco.partida;

import java.util.List;
import java.util.Objects;

/** Uma equipe e seus jogadores: um no 1x1, dois (uma dupla) no 2x2 (RG-ESC-1). */
public record Equipe(EquipeId id, List<JogadorId> jogadores) {

    public Equipe {
        Objects.requireNonNull(id, "id");
        jogadores = List.copyOf(jogadores);
        if (jogadores.isEmpty()) {
            throw new IllegalArgumentException("Uma equipe precisa de pelo menos um jogador");
        }
    }

    /** Equipe de um jogador só, identificada pelo mesmo nome do jogador. */
    public static Equipe individual(JogadorId jogador) {
        return new Equipe(new EquipeId(jogador.valor()), List.of(jogador));
    }
}
