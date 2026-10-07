package io.github.joaomarcosvs.truco.acao;

/** Joga aberta a carta na posição {@code indiceNaMao} da mão (RG-VAZA-1). */
public record JogarCarta(int indiceNaMao) implements Acao {}
