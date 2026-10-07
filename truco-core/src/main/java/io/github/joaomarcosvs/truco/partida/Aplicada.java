package io.github.joaomarcosvs.truco.partida;

import io.github.joaomarcosvs.truco.evento.Evento;
import java.util.List;
import java.util.Objects;

/** A ação foi aceita: o novo estado e os eventos gerados, na ordem em que aconteceram. */
public record Aplicada(EstadoDaPartida novoEstado, List<Evento> eventos) implements Resultado {

    public Aplicada {
        Objects.requireNonNull(novoEstado, "novoEstado");
        eventos = List.copyOf(eventos);
    }
}
