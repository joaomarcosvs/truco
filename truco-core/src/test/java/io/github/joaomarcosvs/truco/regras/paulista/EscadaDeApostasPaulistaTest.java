package io.github.joaomarcosvs.truco.regras.paulista;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.joaomarcosvs.truco.partida.Aposta;
import io.github.joaomarcosvs.truco.partida.EquipeId;
import io.github.joaomarcosvs.truco.regras.EscadaDeApostas;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EscadaDeApostasPaulistaTest {

    private static final EquipeId ANA = new EquipeId("ana");
    private static final EquipeId BETO = new EquipeId("beto");

    private final EscadaDeApostas escada = new TrucoPaulista().escadaDeApostas();

    @Test
    @DisplayName("RG-PARTIDA-3: a rodada começa valendo 1 ponto")
    void valorInicial() {
        assertThat(escada.valorInicial()).isEqualTo(1);
    }

    @Test
    @DisplayName("RG-AUM-1: a escada é 1, 3 (truco), 6 (seis), 9 (nove) e 12 (doze), e o doze é o teto")
    void niveis() {
        assertThat(escada.proximoNivel(1)).hasValue(3);
        assertThat(escada.proximoNivel(3)).hasValue(6);
        assertThat(escada.proximoNivel(6)).hasValue(9);
        assertThat(escada.proximoNivel(9)).hasValue(12);
        assertThat(escada.proximoNivel(12)).isEmpty();
    }

    @ParameterizedTest(name = "{0} recusado: quem pediu ganha {2}")
    @CsvSource({"truco, 3, 1", "seis, 6, 3", "nove, 9, 6", "doze, 12, 9"})
    @DisplayName("RG-AUM-4: quem pediu ganha o valor anterior ao pedido recusado")
    void valorAoCorrer(String nome, int nivelRecusado, int pontos) {
        assertThat(escada.valorAoCorrer(nivelRecusado)).isEqualTo(pontos);
    }

    @ParameterizedTest(name = "rodada valendo {0}")
    @CsvSource({"1", "3", "6", "9", "12"})
    @DisplayName("RG-AUM-6: quem corre na própria vez entrega ao adversário o valor atual da rodada")
    void valorAoDesistir(int valorAtual) {
        assertThat(escada.valorAoDesistir(valorAtual)).isEqualTo(valorAtual);
    }

    @Test
    @DisplayName("RG-AUM-5: antes de qualquer aceite, as duas equipes podem pedir; depois, só a que aceitou")
    void direitoDeAumentar() {
        Aposta aceitaPelaAna = new Aposta(3, Optional.of(ANA), Optional.empty());

        assertThat(escada.podeAumentar(Aposta.inicial(1), ANA)).isTrue();
        assertThat(escada.podeAumentar(Aposta.inicial(1), BETO)).isTrue();
        assertThat(escada.podeAumentar(aceitaPelaAna, ANA)).isTrue();
        assertThat(escada.podeAumentar(aceitaPelaAna, BETO)).isFalse();
    }

    @Test
    @DisplayName("RG-AUM-1: com a rodada valendo 12, ninguém pode pedir aumento")
    void dozeSemAumento() {
        Aposta doze = new Aposta(12, Optional.of(ANA), Optional.empty());

        assertThat(escada.podeAumentar(doze, ANA)).isFalse();
        assertThat(escada.podeAumentar(doze, BETO)).isFalse();
    }

    @Test
    @DisplayName("Valor fora da escada é erro de programação")
    void valorForaDaEscada() {
        assertThatIllegalArgumentException().isThrownBy(() -> escada.proximoNivel(2));
        assertThatIllegalArgumentException().isThrownBy(() -> escada.valorAoCorrer(1));
    }
}
