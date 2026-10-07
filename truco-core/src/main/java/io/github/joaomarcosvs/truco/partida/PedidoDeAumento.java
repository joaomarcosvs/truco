package io.github.joaomarcosvs.truco.partida;

import java.util.Objects;

/** Um pedido de aumento à espera de resposta: quem pediu, quem responde e o nível proposto (RG-AUM-3). */
public record PedidoDeAumento(JogadorId pedinte, JogadorId respondedor, int nivelProposto) {

    public PedidoDeAumento {
        Objects.requireNonNull(pedinte, "pedinte");
        Objects.requireNonNull(respondedor, "respondedor");
    }
}
