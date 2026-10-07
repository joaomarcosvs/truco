package io.github.joaomarcosvs.truco.carta;

import java.util.Arrays;

/** Lê a notação curta usada nos testes, como {@code "4♦"} ou {@code "10♣"}. */
public final class NotacaoDeCartas {

    private NotacaoDeCartas() {}

    /** A carta escrita como valor seguido do naipe, por exemplo {@code carta("10♣")}. */
    public static Carta carta(String notacao) {
        int inicioDoNaipe = notacao.length() - 1;
        return new Carta(valor(notacao.substring(0, inicioDoNaipe)), naipe(notacao.substring(inicioDoNaipe)));
    }

    /** O valor pelo símbolo, por exemplo {@code valor("Q")}. */
    public static Valor valor(String simbolo) {
        return Arrays.stream(Valor.values())
                .filter(valor -> valor.simbolo().equals(simbolo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Valor desconhecido: " + simbolo));
    }

    private static Naipe naipe(String simbolo) {
        return Arrays.stream(Naipe.values())
                .filter(naipe -> naipe.simbolo().equals(simbolo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Naipe desconhecido: " + simbolo));
    }
}
