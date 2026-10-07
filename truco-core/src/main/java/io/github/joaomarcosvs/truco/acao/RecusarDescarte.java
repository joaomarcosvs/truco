package io.github.joaomarcosvs.truco.acao;

/**
 * Não descarta a carta da vez. Todos respondem a cada carta da vez: para o dono dela, isso encerra o descarte
 * (RG-DESC-5); para os outros, é só passar (RG-DESC-10).
 */
public record RecusarDescarte() implements Acao {}
