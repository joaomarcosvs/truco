package io.github.joaomarcosvs.truco.cli;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.Naipe;
import io.github.joaomarcosvs.truco.carta.Valor;
import java.util.Arrays;
import java.util.List;

/** Cartas a partir da notação curta, como "A♥" ou "10♠", para os testes. */
final class Cartas {

    private Cartas() {}

    static Carta carta(String notacao) {
        for (Valor valor : Valor.values()) {
            for (Naipe naipe : Naipe.values()) {
                if ((valor.simbolo() + naipe.simbolo()).equals(notacao)) {
                    return new Carta(valor, naipe);
                }
            }
        }
        throw new IllegalArgumentException("Notação de carta inválida: " + notacao);
    }

    /** Várias cartas separadas por espaço, como "A♥ 3♠ 4♦". */
    static List<Carta> cartas(String notacoes) {
        return Arrays.stream(notacoes.split(" ")).map(Cartas::carta).toList();
    }
}
