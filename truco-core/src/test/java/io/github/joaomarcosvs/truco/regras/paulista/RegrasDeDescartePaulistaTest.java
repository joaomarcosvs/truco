package io.github.joaomarcosvs.truco.regras.paulista;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.carta.Valor;
import io.github.joaomarcosvs.truco.regras.RegrasDeDescarte;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegrasDeDescartePaulistaTest {

    private final RegrasDeDescarte regras = new TrucoPaulista().regrasDeDescarte();

    @Test
    @DisplayName(
            "RG-DESC-3: com vira 7 (manilha 10), a sequência tem todas as cartas menos os 10, da mais fraca para a mais"
                    + " forte, e em cada valor ouros, espadas, copas e paus")
    void sequenciaComVira7() {
        List<Carta> sequencia = regras.sequencia(Optional.of(carta("7♦")));

        assertThat(sequencia.stream().map(Carta::toString).collect(joining(" ")))
                .isEqualTo("4♦ 4♠ 4♥ 4♣ 5♦ 5♠ 5♥ 5♣ 6♦ 6♠ 6♥ 6♣ 7♦ 7♠ 7♥ 7♣ Q♦ Q♠ Q♥ Q♣ J♦ J♠ J♥ J♣ "
                        + "A♦ A♠ A♥ A♣ 2♦ 2♠ 2♥ 2♣ 3♦ 3♠ 3♥ 3♣");
    }

    @Test
    @DisplayName("RG-DESC-3: com vira 3 (manilha 4), a sequência começa em 5♦ e não tem nenhum 4")
    void sequenciaComVira3() {
        List<Carta> sequencia = regras.sequencia(Optional.of(carta("3♠")));

        assertThat(sequencia.getFirst()).isEqualTo(carta("5♦"));
        assertThat(sequencia).hasSize(36).extracting(Carta::valor).doesNotContain(Valor.QUATRO);
    }
}
