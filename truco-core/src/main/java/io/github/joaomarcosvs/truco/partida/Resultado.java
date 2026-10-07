package io.github.joaomarcosvs.truco.partida;

/** O que aconteceu ao aplicar uma ação: aceita, com o novo estado, ou rejeitada, com o motivo. */
public sealed interface Resultado permits Aplicada, Rejeitada {}
