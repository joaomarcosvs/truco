package io.github.joaomarcosvs.truco.carta;

import java.util.Objects;

/**
 * Uma carta, identificada por valor e naipe. Não tem ordem própria: a força depende da variante e, no Truco Paulista,
 * da vira (RG-CARTAS-2 a RG-CARTAS-6).
 */
public record Carta(Valor valor, Naipe naipe) {

    public Carta {
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(naipe, "naipe");
    }

    /** Notação curta: o símbolo do valor seguido do símbolo do naipe, como {@code 4♦} ou {@code 10♣}. */
    @Override
    public String toString() {
        return valor.simbolo() + naipe.simbolo();
    }
}
