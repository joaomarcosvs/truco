package io.github.joaomarcosvs.truco.partida;

import java.util.Objects;

/** Que tipo de rodada está sendo jogada, decidido pelo placar no começo dela (RG-ONZE-1, RG-ESCURINHO-1). */
public sealed interface TipoDeRodada {

    /** Rodada comum. */
    record Normal() implements TipoDeRodada {}

    /**
     * Rodada de Onze: só uma equipe tem 11 pontos e decide, vendo as cartas, se joga ou corre (RG-ONZE-1).
     * {@code decidiuJogar} fica verdadeiro depois que ela decide jogar.
     */
    record DeOnze(EquipeId equipeComOnze, boolean decidiuJogar) implements TipoDeRodada {

        public DeOnze {
            Objects.requireNonNull(equipeComOnze, "equipeComOnze");
        }
    }

    /** Rodada Escurinho: as duas equipes têm 11 pontos, e as cartas são jogadas às cegas (RG-ESCURINHO-1). */
    record Escurinho() implements TipoDeRodada {}
}
