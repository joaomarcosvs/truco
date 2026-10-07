package io.github.joaomarcosvs.truco.regras.paulista;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.Valor;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComposicaoDoBaralhoPaulistaTest {

    private final List<Carta> cartas = new TrucoPaulista().composicaoDoBaralho().cartas();

    @Test
    @DisplayName("RG-CARTAS-1: o baralho tem 40 cartas, sem repetição")
    void quarentaCartasSemRepeticao() {
        assertThat(cartas).hasSize(40).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("RG-CARTAS-1: A, 2, 3, 4, 5, 6, 7, 10, Q e J, cada um em ouros, espadas, copas e paus")
    void valoresNosQuatroNaipes() {
        List<Carta> esperadas = Stream.of("A", "2", "3", "4", "5", "6", "7", "10", "Q", "J")
                .flatMap(valor -> Stream.of("♦", "♠", "♥", "♣").map(naipe -> carta(valor + naipe)))
                .toList();

        assertThat(cartas).containsExactlyInAnyOrderElementsOf(esperadas);
    }

    @Test
    @DisplayName("RG-CARTAS-1: não há 8, 9 nem K")
    void semOitoNoveNemRei() {
        assertThat(cartas).extracting(Carta::valor).doesNotContain(Valor.OITO, Valor.NOVE, Valor.REI);
    }

    @Test
    @DisplayName("A ordem de referência do baralho é fixa, porque o embaralhamento parte dela")
    void ordemDeReferenciaFixa() {
        assertThat(cartas.stream().map(Carta::toString).collect(joining(" ")))
                .isEqualTo("A♦ A♠ A♥ A♣ 2♦ 2♠ 2♥ 2♣ 3♦ 3♠ 3♥ 3♣ 4♦ 4♠ 4♥ 4♣ 5♦ 5♠ 5♥ 5♣ "
                        + "6♦ 6♠ 6♥ 6♣ 7♦ 7♠ 7♥ 7♣ 10♦ 10♠ 10♥ 10♣ Q♦ Q♠ Q♥ Q♣ J♦ J♠ J♥ J♣");
    }

    @Test
    @DisplayName("A lista de cartas não pode ser alterada")
    void listaImutavel() {
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> cartas.add(carta("4♦")));
    }
}
