package io.github.joaomarcosvs.truco.carta;

/**
 * Valor de uma carta do baralho francês. A ordem de declaração não tem significado de regra: quais valores existem e a
 * força de cada um são definidos pela variante (RG-CARTAS-1 e RG-CARTAS-2 no Truco Paulista).
 */
public enum Valor {
    AS("A"),
    DOIS("2"),
    TRES("3"),
    QUATRO("4"),
    CINCO("5"),
    SEIS("6"),
    SETE("7"),
    OITO("8"),
    NOVE("9"),
    DEZ("10"),
    VALETE("J"),
    DAMA("Q"),
    REI("K");

    private final String simbolo;

    Valor(String simbolo) {
        this.simbolo = simbolo;
    }

    /** Símbolo usado na notação curta das cartas, como o {@code 10} de {@code 10♣}. */
    public String simbolo() {
        return simbolo;
    }
}
