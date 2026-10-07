package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.NAO_E_A_VEZ_DO_JOGADOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.evento.JogadorCorreu;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Correr na própria vez, sem pedido pendente, com vira 7♦. Ana dá as cartas e Beto abre a 1ª vaza. */
class CorrerNaVezTest {

    private static MesaDeTeste mesa() {
        return new MesaDeTeste(
                Cenario.comVira("7♦").mao(BETO, "3♠ A♥ 5♦").mao(ANA, "4♣ A♠ 6♦").montar());
    }

    @Test
    @DisplayName("RG-AUM-6 e RG-PARTIDA-5: quem corre na própria vez entrega a rodada ao adversário pelo valor atual")
    void correrNaVez() {
        MesaDeTeste mesa = mesa().correr(BETO);

        assertThat(mesa.eventos(JogadorCorreu.class)).containsExactly(new JogadorCorreu(BETO));
        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DA_ANA, 1));
        assertThat(mesa.estado().placar().pontosDe(EQUIPE_DA_ANA)).isEqualTo(1);
        assertThat(mesa.estado().numeroDaRodada()).isEqualTo(2);
    }

    @Test
    @DisplayName("RG-AUM-6: com o truco aceito, quem corre na própria vez entrega 3 pontos")
    void correrComTrucoAceito() {
        MesaDeTeste mesa = mesa().pedirAumento(BETO).aceitar(ANA).correr(BETO);

        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DA_ANA, 3));
    }

    @Test
    @DisplayName("RG-AUM-6: dá para correr em qualquer vaza, na própria vez")
    void correrNumaVazaSeguinte() {
        MesaDeTeste mesa = mesa().jogar(BETO, "3♠").jogar(ANA, "4♣").jogar(BETO, "5♦");

        mesa.correr(ANA);

        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 1));
    }

    @Test
    @DisplayName("RG-AUM-6: não se corre fora da própria vez")
    void correrForaDaVez() {
        assertThat(MOTOR.aplicar(mesa().estado(), ANA, new Correr())).isEqualTo(new Rejeitada(NAO_E_A_VEZ_DO_JOGADOR));
    }
}
