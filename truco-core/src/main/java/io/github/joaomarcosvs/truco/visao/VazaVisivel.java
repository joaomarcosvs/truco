package io.github.joaomarcosvs.truco.visao;

import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza;
import java.util.List;
import java.util.Objects;

/** Uma vaza encerrada como o jogador a vê: as jogadas, sem as cartas encobertas dos outros, e o resultado. */
public record VazaVisivel(List<JogadaVisivel> jogadas, ResultadoDaVaza resultado) {

    public VazaVisivel {
        jogadas = List.copyOf(jogadas);
        Objects.requireNonNull(resultado, "resultado");
    }
}
