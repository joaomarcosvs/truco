package io.github.joaomarcosvs.truco.acao;

/**
 * Descarta a carta da vez, que está na posição {@code indiceNaMao}, e compra outra do baralho, que fica na mesma posição
 * (RG-DESC-4, RG-DESC-7). Só o dono da carta da vez pode descartar.
 */
public record Descartar(int indiceNaMao) implements Acao {}
