package io.github.joaomarcosvs.truco.partida;

import java.util.List;
import java.util.Objects;

/** Uma equipe e seus jogadores. Na v1 cada equipe tem um jogador, mas o modelo já aceita duplas (RG-ESC-1). */
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
