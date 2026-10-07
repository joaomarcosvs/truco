package io.github.joaomarcosvs.truco.acao;

/**
 * Joga encoberta, virada para baixo, a carta na posição {@code indiceNaMao} da mão. Só a partir da 2ª vaza, e ela não
 * conta na comparação (RG-ENC-1, RG-ENC-2).
 */
public record JogarEncoberta(int indiceNaMao) implements Acao {}
