package io.github.joaomarcosvs.truco.partida;

import java.util.Objects;
import java.util.Optional;

/**
 * A aposta da rodada (RG-AUM-*): quanto ela vale, qual equipe aceitou o último aumento e o pedido de aumento que espera
 * resposta. Tudo aqui é público.
 */
public record Aposta(int valor, Optional<EquipeId> ultimaEquipeQueAceitou, Optional<PedidoDeAumento> pedidoPendente) {

    public Aposta {
        if (valor < 1) {
            throw new IllegalArgumentException("A rodada vale pelo menos 1 ponto: " + valor);
        }
        Objects.requireNonNull(ultimaEquipeQueAceitou, "ultimaEquipeQueAceitou");
        Objects.requireNonNull(pedidoPendente, "pedidoPendente");
    }

    /** A aposta do começo da rodada: sem aumentos aceitos nem pedidos. */
    public static Aposta inicial(int valor) {
        return new Aposta(valor, Optional.empty(), Optional.empty());
    }
}
