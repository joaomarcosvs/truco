package io.github.joaomarcosvs.truco.protocol;

import io.github.joaomarcosvs.truco.carta.Naipe;
import io.github.joaomarcosvs.truco.carta.Valor;

/** Uma carta no JSON: {@code {"valor":"AS","naipe":"COPAS"}}, com os nomes dos enums, só em ASCII. */
record CartaDto(Valor valor, Naipe naipe) {}
