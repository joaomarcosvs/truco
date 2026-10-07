package io.github.joaomarcosvs.truco.partida;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SorteioTest {

    private static final List<Carta> BARALHO =
            new TrucoPaulista().composicaoDoBaralho().cartas();

    @Test
    @DisplayName("Mesma seed e mesma rodada embaralham igual; outra rodada ou outra seed embaralham diferente")
    void deterministico() {
        List<Carta> embaralhadas = Sorteio.embaralhar(BARALHO, 42, 1);

        assertThat(Sorteio.embaralhar(BARALHO, 42, 1)).isEqualTo(embaralhadas);
        assertThat(Sorteio.embaralhar(BARALHO, 42, 2)).isNotEqualTo(embaralhadas);
        assertThat(Sorteio.embaralhar(BARALHO, 43, 1)).isNotEqualTo(embaralhadas);
    }

    @Test
    @DisplayName("O embaralhamento só reordena as cartas")
    void soReordena() {
        assertThat(Sorteio.embaralhar(BARALHO, 7, 3)).containsExactlyInAnyOrderElementsOf(BARALHO);
    }

    @Test
    @DisplayName("O algoritmo não muda: com a seed 2026, a rodada 1 começa sempre pelas mesmas cartas")
    void algoritmoFixo() {
        assertThat(Sorteio.embaralhar(BARALHO, 2026, 1).subList(0, 8))
                .extracting(Carta::toString)
                .containsExactly("5♥", "2♣", "10♣", "3♣", "7♦", "J♦", "Q♦", "Q♣");
    }

    @Test
    @DisplayName("Em 20 mil embaralhamentos, cada carta cai em cada posição com frequência parecida")
    void semVies() {
        int[][] vezes = new int[BARALHO.size()][BARALHO.size()];
        for (int seed = 0; seed < 20_000; seed++) {
            List<Carta> embaralhadas = Sorteio.embaralhar(BARALHO, seed, 1);
            for (int posicao = 0; posicao < embaralhadas.size(); posicao++) {
                vezes[BARALHO.indexOf(embaralhadas.get(posicao))][posicao]++;
            }
        }
        // O esperado é 500 por carta e posição, com desvio padrão perto de 22: a faixa só pega erro de algoritmo.
        assertThat(Arrays.stream(vezes).flatMapToInt(Arrays::stream)).allMatch(n -> n > 350 && n < 650);
    }

    @Test
    @DisplayName("RG-PARTIDA-4: o carteador inicial sai da seed, e qualquer jogador pode ser sorteado")
    void carteadorInicial() {
        assertThat(Sorteio.carteadorInicial(42, 2)).isEqualTo(Sorteio.carteadorInicial(42, 2));
        assertThat(IntStream.range(0, 50).map(seed -> Sorteio.carteadorInicial(seed, 2)))
                .containsOnly(0, 1)
                .contains(0, 1);
    }
}
