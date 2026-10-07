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
import io.github.joaomarcosvs.truco.regras.OrdemDeForca;
import java.util.List;
import java.util.Optional;

/**
 * Força das cartas no Truco Paulista: as comuns seguem uma ordem fixa em que o naipe não conta; as manilhas, definidas
 * pela vira, vencem todas as outras e desempatam pelo naipe (RG-CARTAS-2 a RG-CARTAS-6).
 */
final class OrdemDeForcaPaulista implements OrdemDeForca {

    /** RG-CARTAS-2: da carta comum mais fraca para a mais forte. */
    static final List<Valor> VALORES = List.of(QUATRO, CINCO, SEIS, SETE, DEZ, DAMA, VALETE, AS, DOIS, TRES);

    /** RG-CARTAS-5: da manilha mais fraca para a mais forte (a de paus é o zap). */
    private static final List<Naipe> NAIPES_DAS_MANILHAS = List.of(OUROS, ESPADAS, COPAS, PAUS);

    @Override
    public boolean usaVira() {
        return true; // RG-CARTAS-3 e RG-CARTAS-4: a vira define a manilha.
    }

    @Override
    public int forca(Carta carta, Optional<Carta> vira) {
        Carta cartaVirada = vira.orElseThrow(() -> new IllegalArgumentException("O Truco Paulista sempre tem vira"));
        if (carta.valor() == manilha(cartaVirada)) {
            // RG-CARTAS-5: acima de todas as comuns, desempatando pelo naipe.
            return VALORES.size() + NAIPES_DAS_MANILHAS.indexOf(carta.naipe());
        }
        // RG-CARTAS-2 e RG-CARTAS-6: pela ordem comum, inclusive as de mesmo valor da vira; o naipe não conta.
        return posicao(carta.valor());
    }

    /** RG-CARTAS-4: o valor seguinte ao da vira, em ordem circular (depois do 3 volta o 4). */
    static Valor manilha(Carta vira) {
        return VALORES.get((posicao(vira.valor()) + 1) % VALORES.size());
    }

    private static int posicao(Valor valor) {
        int posicao = VALORES.indexOf(valor);
        if (posicao < 0) {
            throw new IllegalArgumentException("Valor fora do baralho do Truco Paulista: " + valor);
        }
        return posicao;
    }
}
