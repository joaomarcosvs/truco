package io.github.joaomarcosvs.truco.regras.paulista;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PontuacaoDaPartidaPaulistaTest {

    @Test
    @DisplayName("RG-PARTIDA-1: vence a partida quem chega a 12 pontos")
    void pontosParaVencer() {
        assertThat(new TrucoPaulista().pontuacaoDaPartida().pontosParaVencer()).isEqualTo(12);
    }
}
