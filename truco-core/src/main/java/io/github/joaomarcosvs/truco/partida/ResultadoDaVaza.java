package io.github.joaomarcosvs.truco.partida;

import java.util.Objects;

/** Como terminou uma vaza (RG-VAZA-2 e RG-EMP-*). */
public sealed interface ResultadoDaVaza {

    /** A vaza teve vencedor: quem jogou a carta mais forte, e a equipe dele. */
    record Vencida(JogadorId jogador, EquipeId equipe) implements ResultadoDaVaza {

        public Vencida {
            Objects.requireNonNull(jogador, "jogador");
            Objects.requireNonNull(equipe, "equipe");
        }
    }

    /** As cartas mais fortes eram de equipes diferentes e empataram. */
    record Empatada() implements ResultadoDaVaza {}
}
