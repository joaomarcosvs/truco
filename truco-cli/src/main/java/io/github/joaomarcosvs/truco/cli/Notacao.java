package io.github.joaomarcosvs.truco.cli;

import static java.util.stream.Collectors.joining;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.Naipe;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.List;

/** Como as cartas aparecem nos textos para o jogador. */
public enum Notacao {

    /** Com os símbolos de naipe: "A♥", "10♠". */
    SIMBOLOS,

    /** Com letras no lugar dos naipes, para terminais que não mostram os símbolos: "Ac", "10e". */
    LETRAS,

    /**
     * Pelos nomes do baralho espanhol, usado pela página web: "4 de bastos", "ás de espadas", "cavalo de ouros". Paus
     * viram bastos. As figuras seguem a ordem de força do paulista (10 &lt; Q &lt; J), que é a mesma de sota &lt; cavalo
     * &lt; rei: 10 é a sota, Q é o cavalo e J é o rei. O K, que não faz parte do baralho de 40 cartas, aparece como "K".
     */
    BARALHO_ESPANHOL;

    public String de(Carta carta) {
        return switch (this) {
            case SIMBOLOS -> carta.toString();
            case LETRAS -> carta.valor().simbolo() + letra(carta.naipe());
            case BARALHO_ESPANHOL -> nomeEspanhol(carta);
        };
    }

    /** Várias cartas: separadas por espaço ("2♣ 3♣ J♦") ou, pelos nomes, como numa frase ("2 de bastos e ás de ouros"). */
    public String de(List<Carta> cartas) {
        List<String> nomes = cartas.stream().map(this::de).toList();
        if (this != BARALHO_ESPANHOL || nomes.size() < 2) {
            return String.join(" ", nomes);
        }
        return nomes.subList(0, nomes.size() - 1).stream().collect(joining(", ")) + " e " + nomes.getLast();
    }

    /**
     * A notação que o terminal consegue mostrar. O terminal do Windows usa por padrão uma página de código sem os
     * símbolos de naipe (a não ser com {@code chcp 65001}).
     */
    public static Notacao para(Charset charset) {
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

    private static String nomeEspanhol(Carta carta) {
        String valor = switch (carta.valor()) {
            case AS -> "ás";
            case DEZ -> "sota";
            case DAMA -> "cavalo";
            case VALETE -> "rei";
            default -> carta.valor().simbolo();
        };
        String naipe = switch (carta.naipe()) {
            case COPAS -> "copas";
            case ESPADAS -> "espadas";
            case OUROS -> "ouros";
            case PAUS -> "bastos";
        };
        return valor + " de " + naipe;
    }
}
