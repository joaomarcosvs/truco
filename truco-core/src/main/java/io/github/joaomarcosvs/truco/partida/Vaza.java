package io.github.joaomarcosvs.truco.partida;

import java.util.List;
import java.util.Objects;

/** Uma vaza encerrada: as jogadas, na ordem em que foram feitas, e o resultado (RG-VAZA-1). */
public record Vaza(List<Jogada> jogadas, ResultadoDaVaza resultado) {

    public Vaza {
        jogadas = List.copyOf(jogadas);
        Objects.requireNonNull(resultado, "resultado");
        if (jogadas.isEmpty()) {
            throw new IllegalArgumentException("Uma vaza tem pelo menos uma jogada");
        }
    }

    /** Quem abriu a vaza: o autor da primeira jogada. */
    public JogadorId abridor() {
        return jogadas.getFirst().jogador();
    }
}
