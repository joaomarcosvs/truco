package io.github.joaomarcosvs.truco.regras;

import io.github.joaomarcosvs.truco.carta.Carta;
import java.util.List;

/** Quais cartas existem no baralho de uma variante (RG-CARTAS-1 no Truco Paulista). */
public interface ComposicaoDoBaralho {

    /**
     * As cartas do baralho, sem repetição, numa ordem de referência fixa. O embaralhamento parte dessa ordem, então
     * mudá-la muda as partidas geradas por uma mesma seed.
     */
    List<Carta> cartas();
}
