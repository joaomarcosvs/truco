package io.github.joaomarcosvs.truco.acao;

/**
 * Quer ver as cartas do parceiro, mostrando as próprias. Só vale para a dupla que recebeu um pedido de aumento, enquanto
 * ele espera resposta; quando todos os parceiros querem, eles se veem até a resposta (RG-2X2-9, RG-2X2-10).
 */
public record VerCartasDoParceiro() implements Acao {}
