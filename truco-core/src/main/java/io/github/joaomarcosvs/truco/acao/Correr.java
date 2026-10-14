package io.github.joaomarcosvs.truco.acao;

/**
 * Desiste da rodada. Só vale em resposta a um pedido de aumento, e então quem pediu ganha o valor anterior ao pedido
 * (RG-AUM-3, RG-AUM-4), ou na decisão da Rodada de Onze (RG-ONZE-1). Na própria vez não se corre (RG-AUM-6).
 */
public record Correr() implements Acao {}
