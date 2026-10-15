package io.github.joaomarcosvs.truco.cli;

import static io.github.joaomarcosvs.truco.cli.Cartas.carta;
import static io.github.joaomarcosvs.truco.cli.Cartas.cartas;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NotacaoTest {

    @ParameterizedTest(name = "{0}: com símbolos {0}, com letras {1}")
    @CsvSource({"A♥, Ac", "10♠, 10e", "7♦, 7o", "4♣, 4p", "Q♥, Qc", "J♣, Jp"})
    @DisplayName("Com símbolos, a carta aparece como no core; com letras, c, e, o e p no lugar dos naipes")
    void notacoes(String simbolos, String letras) {
        assertThat(Notacao.SIMBOLOS.de(carta(simbolos))).isEqualTo(simbolos);
        assertThat(Notacao.LETRAS.de(carta(simbolos))).isEqualTo(letras);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
        "A♠, ás de espadas",
        "4♣, 4 de bastos",
        "7♦, 7 de ouros",
        "10♥, sota de copas",
        "Q♦, cavalo de ouros",
        "J♣, rei de bastos"
    })
    @DisplayName("Baralho espanhol: paus viram bastos, e 10, Q e J viram sota, cavalo e rei, na mesma ordem de força")
    void baralhoEspanhol(String simbolos, String nome) {
        assertThat(Notacao.BARALHO_ESPANHOL.de(carta(simbolos))).isEqualTo(nome);
    }

    @Test
    @DisplayName("Várias cartas: separadas por espaço, ou como numa frase no baralho espanhol")
    void variasCartas() {
        assertThat(Notacao.SIMBOLOS.de(cartas("2♣ 3♣ J♦"))).isEqualTo("2♣ 3♣ J♦");
        assertThat(Notacao.BARALHO_ESPANHOL.de(cartas("2♣ 3♣ J♦")))
                .isEqualTo("2 de bastos, 3 de bastos e rei de ouros");
        assertThat(Notacao.BARALHO_ESPANHOL.de(cartas("4♦"))).isEqualTo("4 de ouros");
    }

    @Test
    @DisplayName("O terminal em UTF-8 mostra os símbolos; um que não os codifica recebe letras")
    void notacaoPeloTerminal() {
        assertThat(Notacao.para(StandardCharsets.UTF_8)).isEqualTo(Notacao.SIMBOLOS);
        assertThat(Notacao.para(StandardCharsets.US_ASCII)).isEqualTo(Notacao.LETRAS);
        assertThat(Notacao.para(StandardCharsets.ISO_8859_1)).isEqualTo(Notacao.LETRAS);
    }
}
