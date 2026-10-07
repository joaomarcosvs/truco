package io.github.joaomarcosvs.truco.regras.paulista;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.valor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.Valor;
import io.github.joaomarcosvs.truco.regras.OrdemDeForca;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tabelas e exemplos das seções 2 e 13 do documento de regras. */
class OrdemDeForcaPaulistaTest {

    private final OrdemDeForca ordem = new TrucoPaulista().ordemDeForca();
    private final List<Carta> baralho =
            new TrucoPaulista().composicaoDoBaralho().cartas();

    @Test
    @DisplayName("RG-CARTAS-2: as cartas comuns seguem 4 < 5 < 6 < 7 < 10 < Q < J < A < 2 < 3")
    void ordemDasCartasComuns() {
        // Com vira 3♠ a manilha é o 4, e com vira 2♦ é o 3: juntas, as duas sequências cobrem a ordem inteira.
        assertOrdemCrescente(vira("3♠"), "5♦", "6♠", "7♥", "10♣", "Q♦", "J♠", "A♥", "2♣", "3♦");
        assertOrdemCrescente(vira("2♦"), "4♣", "5♥", "6♠", "7♦", "10♥", "Q♣", "J♦", "A♠", "2♥");
    }

    @Test
    @DisplayName("RG-CARTAS-2: cartas comuns de mesmo valor empatam, sem desempate pelo naipe (A♥ contra A♠)")
    void mesmoValorEmpata() {
        assertThat(ordem.comparar(carta("A♥"), carta("A♠"), vira("7♦"))).isZero();
    }

    @ParameterizedTest(name = "vira {0} → manilha {1}")
    @CsvSource({"4♣, 5", "5♦, 6", "6♠, 7", "7♦, 10", "10♥, Q", "Q♣, J", "J♥, A", "A♥, 2", "2♠, 3", "3♠, 4"})
    @DisplayName("RG-CARTAS-4: a manilha é o valor seguinte ao da vira, em ordem circular")
    void manilhaPelaVira(String notacaoDaVira, String simboloDaManilha) {
        Valor manilha = valor(simboloDaManilha);
        List<Carta> manilhas =
                baralho.stream().filter(carta -> carta.valor() == manilha).toList();
        List<Carta> outras =
                baralho.stream().filter(carta -> carta.valor() != manilha).toList();

        for (Carta cartaDaManilha : manilhas) {
            for (Carta outra : outras) {
                assertThat(ordem.comparar(cartaDaManilha, outra, vira(notacaoDaVira)))
                        .as("%s deve vencer %s", cartaDaManilha, outra)
                        .isPositive();
            }
        }
    }

    @Test
    @DisplayName("RG-CARTAS-5: com vira 7♦, 10♦ < 10♠ < 10♥ < 10♣ (zap) e qualquer 10 vence um 3")
    void manilhasDesempatamPeloNaipe() {
        assertOrdemCrescente(vira("7♦"), "10♦", "10♠", "10♥", "10♣");
        for (String dez : List.of("10♦", "10♠", "10♥", "10♣")) {
            for (String tres : List.of("3♦", "3♠", "3♥", "3♣")) {
                assertThat(ordem.comparar(carta(dez), carta(tres), vira("7♦")))
                        .as("%s deve vencer %s", dez, tres)
                        .isPositive();
            }
        }
    }

    @Test
    @DisplayName("RG-CARTAS-5: com vira 3♠, o 4♣ é a carta mais forte da rodada")
    void zapComViraTres() {
        Carta zap = carta("4♣");
        for (Carta outra : baralho) {
            if (!outra.equals(zap)) {
                assertThat(ordem.comparar(zap, outra, vira("3♠")))
                        .as("4♣ deve vencer %s", outra)
                        .isPositive();
            }
        }
    }

    @Test
    @DisplayName("RG-CARTAS-6: com vira 7♦, os outros 7 continuam cartas comuns")
    void mesmoValorDaViraEhCartaComum() {
        assertThat(ordem.comparar(carta("7♣"), carta("7♥"), vira("7♦"))).isZero();
        assertOrdemCrescente(vira("7♦"), "6♣", "7♣", "Q♦", "3♦", "10♦");
    }

    @Test
    @DisplayName("RG-CARTAS-3 e RG-CARTAS-4: o Truco Paulista vira uma carta para definir a manilha")
    void usaVira() {
        assertThat(ordem.usaVira()).isTrue();
    }

    @Test
    @DisplayName("Comparar cartas do Truco Paulista sem vira é erro de programação")
    void exigeVira() {
        assertThatIllegalArgumentException().isThrownBy(() -> ordem.forca(carta("4♦"), Optional.empty()));
    }

    @Test
    @DisplayName("Avaliar uma carta fora do baralho do Truco Paulista é erro de programação")
    void rejeitaCartaForaDoBaralho() {
        assertThatIllegalArgumentException().isThrownBy(() -> ordem.forca(carta("K♣"), vira("7♦")));
    }

    private static Optional<Carta> vira(String notacao) {
        return Optional.of(carta(notacao));
    }

    /** Confere que cada carta vence a anterior e que a anterior perde para ela. */
    private void assertOrdemCrescente(Optional<Carta> vira, String... daMaisFracaParaAMaisForte) {
        for (int i = 1; i < daMaisFracaParaAMaisForte.length; i++) {
            Carta anterior = carta(daMaisFracaParaAMaisForte[i - 1]);
            Carta atual = carta(daMaisFracaParaAMaisForte[i]);
            assertThat(ordem.comparar(atual, anterior, vira))
                    .as("%s deve vencer %s", atual, anterior)
                    .isPositive();
            assertThat(ordem.comparar(anterior, atual, vira))
                    .as("%s deve perder para %s", anterior, atual)
                    .isNegative();
        }
    }
}
