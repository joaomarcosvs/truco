package io.github.joaomarcosvs.truco.partida;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A configuração já aceita duplas, embora o motor da v1 só jogue 1x1. */
class ConfiguracaoDaPartidaTest {

    private static final JogadorId A1 = new JogadorId("a1");
    private static final JogadorId A2 = new JogadorId("a2");
    private static final JogadorId B1 = new JogadorId("b1");
    private static final JogadorId B2 = new JogadorId("b2");
    private static final EquipeId A = new EquipeId("a");
    private static final EquipeId B = new EquipeId("b");

    private final ConfiguracaoDaPartida duplas = duplas(new Equipe(A, List.of(A1, A2)), new Equipe(B, List.of(B1, B2)));

    @Test
    @DisplayName("Na mesa, as equipes se alternam")
    void equipesSeAlternam() {
        assertThat(duplas.jogadoresNaOrdemDaMesa()).containsExactly(A1, B1, A2, B2);
    }

    @Test
    @DisplayName("RG-PARTIDA-4: à direita de cada jogador está o seguinte da mesa, e à direita do último, o primeiro")
    void aDireita() {
        assertThat(duplas.aDireitaDe(A1)).isEqualTo(B1);
        assertThat(duplas.aDireitaDe(B1)).isEqualTo(A2);
        assertThat(duplas.aDireitaDe(A2)).isEqualTo(B2);
        assertThat(duplas.aDireitaDe(B2)).isEqualTo(A1);
    }

    @Test
    @DisplayName("A ordem a partir de um jogador segue para a direita")
    void ordemAPartirDeUmJogador() {
        assertThat(duplas.jogadoresAPartirDe(A2)).containsExactly(A2, B2, A1, B1);
    }

    @Test
    @DisplayName("Cada jogador pertence a uma equipe")
    void equipeDoJogador() {
        assertThat(duplas.equipeDe(A2)).isEqualTo(A);
        assertThat(duplas.equipeDe(B1)).isEqualTo(B);
        assertThat(duplas.participa(B2)).isTrue();
        assertThat(duplas.participa(new JogadorId("zeca"))).isFalse();
    }

    @Test
    @DisplayName("Configurações inconsistentes são erro de programação")
    void configuracoesInconsistentes() {
        assertThatIllegalArgumentException().isThrownBy(() -> duplas(new Equipe(A, List.of(A1))));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> duplas(new Equipe(A, List.of(A1)), new Equipe(B, List.of(A1))));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> duplas(new Equipe(A, List.of(A1, A2)), new Equipe(B, List.of(B1))));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> duplas(new Equipe(A, List.of(A1)), new Equipe(A, List.of(B1))));
    }

    private static ConfiguracaoDaPartida duplas(Equipe... equipes) {
        return new ConfiguracaoDaPartida(List.of(equipes), new TrucoPaulista(), 0);
    }
}
