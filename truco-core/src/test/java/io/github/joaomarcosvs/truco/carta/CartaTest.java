package io.github.joaomarcosvs.truco.carta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CartaTest {

    @Test
    @DisplayName("A notação curta junta o símbolo do valor e o do naipe")
    void notacaoCurta() {
        assertThat(new Carta(Valor.QUATRO, Naipe.OUROS)).hasToString("4♦");
        assertThat(new Carta(Valor.DEZ, Naipe.PAUS)).hasToString("10♣");
        assertThat(new Carta(Valor.DAMA, Naipe.COPAS)).hasToString("Q♥");
        assertThat(new Carta(Valor.AS, Naipe.ESPADAS)).hasToString("A♠");
    }

    @Test
    @DisplayName("Cartas com o mesmo valor e o mesmo naipe são iguais")
    void igualdadePorValorENaipe() {
        assertThat(new Carta(Valor.SETE, Naipe.OUROS))
                .isEqualTo(new Carta(Valor.SETE, Naipe.OUROS))
                .isNotEqualTo(new Carta(Valor.SETE, Naipe.COPAS))
                .isNotEqualTo(new Carta(Valor.SEIS, Naipe.OUROS));
    }

    @Test
    @DisplayName("Valor e naipe são obrigatórios")
    void valorENaipeObrigatorios() {
        assertThatNullPointerException().isThrownBy(() -> new Carta(null, Naipe.OUROS));
        assertThatNullPointerException().isThrownBy(() -> new Carta(Valor.AS, null));
    }

    @Test
    @DisplayName("A notação dos testes lê de volta qualquer carta escrita pelo toString")
    void notacaoDeIdaEVolta() {
        for (Valor valor : Valor.values()) {
            for (Naipe naipe : Naipe.values()) {
                Carta carta = new Carta(valor, naipe);
                assertThat(NotacaoDeCartas.carta(carta.toString())).isEqualTo(carta);
            }
        }
    }
}
