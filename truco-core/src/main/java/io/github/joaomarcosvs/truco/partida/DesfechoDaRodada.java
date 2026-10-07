package io.github.joaomarcosvs.truco.partida;

import java.util.Objects;

/** Como terminou uma rodada. */
public sealed interface DesfechoDaRodada {

    /** A equipe venceu a rodada e marca o valor dela (RG-VAZA-3). */
    record Vitoria(EquipeId equipe) implements DesfechoDaRodada {

        public Vitoria {
            Objects.requireNonNull(equipe, "equipe");
        }
    }

    /** As três vazas empataram: ninguém pontua (RG-EMP-5). */
    record Anulada() implements DesfechoDaRodada {}
}
