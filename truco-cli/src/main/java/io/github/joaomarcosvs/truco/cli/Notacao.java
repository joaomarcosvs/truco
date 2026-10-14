package io.github.joaomarcosvs.truco.cli;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.Naipe;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;

/** Como as cartas aparecem no terminal. */
enum Notacao {

    /** Com os símbolos de naipe: "A♥", "10♠". */
    SIMBOLOS,

    /** Com letras no lugar dos naipes, para terminais que não mostram os símbolos: "Ac", "10e". */
    LETRAS;

    String de(Carta carta) {
        return switch (this) {
            case SIMBOLOS -> carta.toString();
            case LETRAS -> carta.valor().simbolo() + letra(carta.naipe());
        };
    }

    /**
     * A notação que o terminal consegue mostrar. O terminal do Windows usa por padrão uma página de código sem os
     * símbolos de naipe (a não ser com {@code chcp 65001}).
     */
    static Notacao para(Charset charset) {
        if (!charset.canEncode()) {
            return LETRAS;
        }
        CharsetEncoder codificador = charset.newEncoder();
        boolean mostraNaipes = Arrays.stream(Naipe.values()).allMatch(naipe -> codificador.canEncode(naipe.simbolo()));
        return mostraNaipes ? SIMBOLOS : LETRAS;
    }

    private static String letra(Naipe naipe) {
        return switch (naipe) {
            case COPAS -> "c";
            case ESPADAS -> "e";
            case OUROS -> "o";
            case PAUS -> "p";
        };
    }
}
