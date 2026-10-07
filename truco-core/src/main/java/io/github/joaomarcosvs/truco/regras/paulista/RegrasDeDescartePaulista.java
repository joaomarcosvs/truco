package io.github.joaomarcosvs.truco.regras.paulista;

import static io.github.joaomarcosvs.truco.carta.Naipe.COPAS;
import static io.github.joaomarcosvs.truco.carta.Naipe.ESPADAS;
import static io.github.joaomarcosvs.truco.carta.Naipe.OUROS;
import static io.github.joaomarcosvs.truco.carta.Naipe.PAUS;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.Naipe;
import io.github.joaomarcosvs.truco.carta.Valor;
import io.github.joaomarcosvs.truco.regras.RegrasDeDescarte;
import java.util.List;
import java.util.Optional;

/** Descarte do Truco Paulista: a sequência vai das cartas comuns mais fracas às mais fortes (RG-DESC-3). */
final class RegrasDeDescartePaulista implements RegrasDeDescarte {

    /** RG-DESC-3: dentro do mesmo valor, ouros, espadas, copas e paus. */
    private static final List<Naipe> NAIPES = List.of(OUROS, ESPADAS, COPAS, PAUS);

    @Override
    public List<Carta> sequencia(Optional<Carta> vira) {
        // RG-DESC-3: todas as cartas menos as manilhas, na ordem de força de RG-CARTAS-2.
        Valor manilha = OrdemDeForcaPaulista.manilha(
                vira.orElseThrow(() -> new IllegalArgumentException("O Truco Paulista sempre tem vira")));
        return OrdemDeForcaPaulista.VALORES.stream()
                .filter(valor -> valor != manilha)
                .flatMap(valor -> NAIPES.stream().map(naipe -> new Carta(valor, naipe)))
                .toList();
    }
}
