package io.github.joaomarcosvs.truco.partida;

import io.github.joaomarcosvs.truco.carta.Carta;
import java.util.List;
import java.util.Set;

/**
 * O descarte da rodada (RG-DESC-*): as cartas já descartadas, se a fase acabou e, na carta da vez, quem já respondeu e
 * se o dono dela decidiu descartá-la. A carta da vez é a seguinte da sequência depois das descartadas.
 *
 * <p>Todos respondem a cada carta da vez, e o passo só se resolve quando todos responderam; por isso quem respondeu e a
 * decisão do dono ficam só no servidor (RG-DESC-10).
 */
public record Descarte(List<Carta> descartadas, boolean encerrado, Set<JogadorId> jaResponderam, boolean donoDescarta) {

    public Descarte {
        descartadas = List.copyOf(descartadas);
        jaResponderam = Set.copyOf(jaResponderam);
    }

    /** Descarte no começo da fase: nada descartado e ninguém respondeu. */
    public static Descarte inicial() {
        return new Descarte(List.of(), false, Set.of(), false);
    }

    /** Descarte já encerrado, com as cartas descartadas. */
    public static Descarte encerrado(List<Carta> descartadas) {
        return new Descarte(descartadas, true, Set.of(), false);
    }
}
