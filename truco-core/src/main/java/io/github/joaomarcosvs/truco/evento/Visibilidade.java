package io.github.joaomarcosvs.truco.evento;

import io.github.joaomarcosvs.truco.partida.JogadorId;
import java.util.Objects;

/** Quem pode ver um evento: todos os jogadores ou só um deles (RG-VIS-1, RG-VIS-2). */
public sealed interface Visibilidade {

    /** Visível para todos os jogadores. */
    Visibilidade PUBLICO = new Publico();

    /** Se o jogador pode ver o evento. */
    boolean permite(JogadorId jogador);

    /** Visível para todos os jogadores. */
    record Publico() implements Visibilidade {

        @Override
        public boolean permite(JogadorId jogador) {
            return true;
        }
    }

    /** Visível só para um jogador. */
    record Privado(JogadorId jogador) implements Visibilidade {

        public Privado {
            Objects.requireNonNull(jogador, "jogador");
        }

        @Override
        public boolean permite(JogadorId outro) {
            return jogador.equals(outro);
        }
    }
}
