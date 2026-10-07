package io.github.joaomarcosvs.truco.acao;

/**
 * Desiste da rodada. Em resposta a um pedido de aumento, quem pediu ganha o valor anterior ao pedido (RG-AUM-3,
 * RG-AUM-4); na própria vez, o adversário ganha o valor atual da rodada (RG-AUM-6).
 */
public record Correr() implements Acao {}
