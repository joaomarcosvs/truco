package io.github.joaomarcosvs.truco.partida;

import java.util.List;
import java.util.Objects;

/**
 * Um pedido de aumento à espera de resposta: quem pediu, quem responde, o nível proposto (RG-AUM-3) e, no 2x2, quem da
 * dupla que responde já quis ver as cartas do parceiro, na ordem em que quis (RG-2X2-9). Tudo aqui é público.
 */
public record PedidoDeAumento(
        JogadorId pedinte, JogadorId respondedor, int nivelProposto, List<JogadorId> queremVerAsCartas) {

    public PedidoDeAumento {
        Objects.requireNonNull(pedinte, "pedinte");
        Objects.requireNonNull(respondedor, "respondedor");
        queremVerAsCartas = List.copyOf(queremVerAsCartas);
    }

    /** Um pedido novo, em que ninguém quis ver as cartas ainda. */
    public PedidoDeAumento(JogadorId pedinte, JogadorId respondedor, int nivelProposto) {
        this(pedinte, respondedor, nivelProposto, List.of());
    }
}
