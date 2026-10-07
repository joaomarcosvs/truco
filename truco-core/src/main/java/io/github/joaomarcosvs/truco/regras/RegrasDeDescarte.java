package io.github.joaomarcosvs.truco.regras;

import io.github.joaomarcosvs.truco.carta.Carta;
import java.util.List;
import java.util.Optional;

/** Como funciona o descarte antes da 1ª vaza (RG-DESC-* no Truco Paulista). */
public interface RegrasDeDescarte {

    /**
     * A sequência de descarte da rodada, na ordem em que as cartas viram "carta da vez"; vazia se a rodada não tem
     * descarte.
     */
    List<Carta> sequencia(Optional<Carta> vira);
}
