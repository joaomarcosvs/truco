package io.github.joaomarcosvs.truco.regras.manilhasfixas;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.regras.OrdemDeForca;
import io.github.joaomarcosvs.truco.regras.VarianteDeRegras;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** As peças da variante de manilhas fixas do teste de prontidão (M10). */
class ManilhasFixasTest {

    private final VarianteDeRegras variante = new ManilhasFixas();
    private final OrdemDeForca ordem = variante.ordemDeForca();

    private int forca(String carta) {
        return ordem.forca(carta(carta), Optional.empty());
    }

    @Test
    @DisplayName("M10: as manilhas são fixas, 4♣ > 7♥ > A♠ > 7♦, e vencem todas as outras cartas, inclusive o 3")
    void manilhasFixas() {
        assertThat(forca("4♣")).isGreaterThan(forca("7♥"));
        assertThat(forca("7♥")).isGreaterThan(forca("A♠"));
        assertThat(forca("A♠")).isGreaterThan(forca("7♦"));
        assertThat(forca("7♦")).isGreaterThan(forca("3♠"));
    }

    @Test
    @DisplayName("M10: fora as manilhas, vale a ordem de valores, e o naipe não importa")
    void cartasComuns() {
        List<Carta> baralho = variante.composicaoDoBaralho().cartas();
        List<String> doMaisFracoAoMaisForte = baralho.stream()
                .sorted(Comparator.comparingInt((Carta carta) -> ordem.forca(carta, Optional.empty())))
                .map(Carta::toString)
                .toList();

        assertThat(doMaisFracoAoMaisForte.subList(baralho.size() - 4, baralho.size()))
                .containsExactly("7♦", "A♠", "7♥", "4♣");
        assertThat(forca("4♦")).isEqualTo(forca("4♥")).isLessThan(forca("5♣"));
        assertThat(forca("7♠")).isEqualTo(forca("7♣")).isLessThan(forca("10♦"));
        assertThat(forca("A♥")).isEqualTo(forca("A♦")).isLessThan(forca("2♠"));
        assertThat(forca("2♠")).isLessThan(forca("3♦"));
    }

    @Test
    @DisplayName("M10: não há vira, e avaliar uma carta com vira é erro de programação")
    void semVira() {
        assertThat(ordem.usaVira()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> ordem.forca(carta("3♠"), Optional.of(carta("4♦"))));
    }

    @Test
    @DisplayName("M10: não há descarte, e o resto vem do Truco Paulista: baralho de 40, 3 cartas, escada e 12 pontos")
    void restoDoPaulista() {
        assertThat(variante.regrasDeDescarte().sequencia(Optional.empty())).isEmpty();
        assertThat(variante.composicaoDoBaralho().cartas()).hasSize(40);
        assertThat(variante.regrasDeVaza().cartasPorJogador()).isEqualTo(3);
        assertThat(variante.escadaDeApostas().valorInicial()).isEqualTo(1);
        assertThat(variante.pontuacaoDaPartida().pontosParaVencer()).isEqualTo(12);
    }
}
