package io.github.joaomarcosvs.truco.partida;

import java.util.Objects;

/** Identificador de uma equipe na partida. */
public record EquipeId(String valor) {

    public EquipeId {
        Objects.requireNonNull(valor, "valor");
        if (valor.isBlank()) {
            throw new IllegalArgumentException("O identificador da equipe não pode ser vazio");
        }
    }

    @Override
    public String toString() {
        return valor;
    }
}
