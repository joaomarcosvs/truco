package io.github.joaomarcosvs.truco.regras.paulista;

import static io.github.joaomarcosvs.truco.carta.Naipe.COPAS;
import static io.github.joaomarcosvs.truco.carta.Naipe.ESPADAS;
import static io.github.joaomarcosvs.truco.carta.Naipe.OUROS;
import static io.github.joaomarcosvs.truco.carta.Naipe.PAUS;
import static io.github.joaomarcosvs.truco.carta.Valor.AS;
import static io.github.joaomarcosvs.truco.carta.Valor.CINCO;
import static io.github.joaomarcosvs.truco.carta.Valor.DAMA;
import static io.github.joaomarcosvs.truco.carta.Valor.DEZ;
import static io.github.joaomarcosvs.truco.carta.Valor.DOIS;
import static io.github.joaomarcosvs.truco.carta.Valor.QUATRO;
import static io.github.joaomarcosvs.truco.carta.Valor.SEIS;
import static io.github.joaomarcosvs.truco.carta.Valor.SETE;
import static io.github.joaomarcosvs.truco.carta.Valor.TRES;
import static io.github.joaomarcosvs.truco.carta.Valor.VALETE;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.Naipe;
import io.github.joaomarcosvs.truco.carta.Valor;
import io.github.joaomarcosvs.truco.regras.ComposicaoDoBaralho;
import java.util.List;

/** Baralho do Truco Paulista: A, 2, 3, 4, 5, 6, 7, 10, Q e J nos quatro naipes, sem 8, 9 e K (RG-CARTAS-1). */
final class ComposicaoDoBaralhoPaulista implements ComposicaoDoBaralho {

    private static final List<Valor> VALORES = List.of(AS, DOIS, TRES, QUATRO, CINCO, SEIS, SETE, DEZ, DAMA, VALETE);
    private static final List<Naipe> NAIPES = List.of(OUROS, ESPADAS, COPAS, PAUS);

    /** Ordem de referência: por valor, na ordem em que RG-CARTAS-1 os lista, e por naipe dentro de cada valor. */
    private static final List<Carta> CARTAS = VALORES.stream()
            .flatMap(valor -> NAIPES.stream().map(naipe -> new Carta(valor, naipe)))
            .toList();

    @Override
    public List<Carta> cartas() {
        return CARTAS;
    }
}
