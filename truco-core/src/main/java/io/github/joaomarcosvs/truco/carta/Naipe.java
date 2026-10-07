package io.github.joaomarcosvs.truco.carta;

/**
 * Naipe de uma carta. A ordem de declaração não tem significado de regra: a força dos naipes, quando existe, é definida
 * pela variante (RG-CARTAS-5 no Truco Paulista).
 */
public enum Naipe {
    COPAS("♥"),
    ESPADAS("♠"),
    OUROS("♦"),
    PAUS("♣");

    private final String simbolo;

    Naipe(String simbolo) {
        this.simbolo = simbolo;
    }

    /** Símbolo usado na notação curta das cartas, como o {@code ♦} de {@code 4♦}. */
    public String simbolo() {
        return simbolo;
    }
}
