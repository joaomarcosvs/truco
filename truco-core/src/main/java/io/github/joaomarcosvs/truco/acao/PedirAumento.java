package io.github.joaomarcosvs.truco.acao;

/**
 * Pede o próximo nível da escada de apostas (RG-AUM-1, RG-AUM-2). Com um pedido pendente, significa "aceito e aumento"
 * (RG-AUM-3).
 */
public record PedirAumento() implements Acao {}
