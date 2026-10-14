package io.github.joaomarcosvs.truco.cli;

import static io.github.joaomarcosvs.truco.cli.Cartas.carta;
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

    @Test
    @DisplayName("O terminal em UTF-8 mostra os símbolos; um que não os codifica recebe letras")
    void notacaoPeloTerminal() {
        assertThat(Notacao.para(StandardCharsets.UTF_8)).isEqualTo(Notacao.SIMBOLOS);
        assertThat(Notacao.para(StandardCharsets.US_ASCII)).isEqualTo(Notacao.LETRAS);
        assertThat(Notacao.para(StandardCharsets.ISO_8859_1)).isEqualTo(Notacao.LETRAS);
    }
}
