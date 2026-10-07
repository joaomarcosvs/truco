package io.github.joaomarcosvs.truco.acao;

/** Recusa o aumento pedido: a rodada acaba e quem pediu ganha o valor anterior ao pedido (RG-AUM-3, RG-AUM-4). */
public record Correr() implements Acao {}
