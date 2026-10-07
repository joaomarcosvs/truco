package io.github.joaomarcosvs.truco.partida;

import java.util.Objects;

/** Identificador de um jogador na partida. */
public record JogadorId(String valor) {

    public JogadorId {
        Objects.requireNonNull(valor, "valor");
        if (valor.isBlank()) {
            throw new IllegalArgumentException("O identificador do jogador não pode ser vazio");
        }
    }

    @Override
    public String toString() {
        return valor;
    }
}
