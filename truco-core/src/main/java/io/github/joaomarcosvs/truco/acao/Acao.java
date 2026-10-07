package io.github.joaomarcosvs.truco.acao;

/**
 * Uma ação de um jogador. As cartas são indicadas pela posição na mão atual ({@code indiceNaMao}): quando uma carta é
 * jogada, as que estavam depois dela sobem uma posição.
 */
public sealed interface Acao
        permits JogarCarta, JogarEncoberta, Descartar, RecusarDescarte, PedirAumento, Aceitar, Correr {}
