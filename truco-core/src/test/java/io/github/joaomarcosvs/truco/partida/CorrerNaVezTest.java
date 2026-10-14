package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.ACAO_INVALIDA;
import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.NAO_E_A_VEZ_DO_JOGADOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Na própria vez não se corre (RG-AUM-6): só respondendo a um pedido de aumento ou na decisão da Rodada de Onze. Vira
 * 7♦; Ana dá as cartas e Beto abre a 1ª vaza.
 */
class CorrerNaVezTest {

    private static MesaDeTeste mesa() {
        return new MesaDeTeste(
                Cenario.comVira("7♦").mao(BETO, "3♠ A♥ 5♦").mao(ANA, "4♣ A♠ 6♦").montar());
    }

    @Test
    @DisplayName("RG-AUM-6: na própria vez, sem pedido de aumento, não se corre, nem com as cartas fracas")
    void naoSeCorreNaVez() {
        EstadoDaPartida estado = mesa().estado();

        assertThat(MOTOR.acoesLegais(estado, BETO))
                .containsExactly(new JogarCarta(0), new JogarCarta(1), new JogarCarta(2), new PedirAumento());
        assertThat(MOTOR.aplicar(estado, BETO, new Correr())).isEqualTo(new Rejeitada(ACAO_INVALIDA));
    }

    @Test
    @DisplayName("RG-AUM-6: depois de um aumento aceito, também não se corre na própria vez")
    void naoSeCorreComTrucoAceito() {
        EstadoDaPartida estado = mesa().pedirAumento(BETO).aceitar(ANA).estado();

        assertThat(MOTOR.acoesLegais(estado, BETO)).doesNotContain(new Correr());
        assertThat(MOTOR.aplicar(estado, BETO, new Correr())).isEqualTo(new Rejeitada(ACAO_INVALIDA));
    }

    @Test
    @DisplayName("RG-AUM-6: em nenhuma vaza se corre na própria vez")
    void naoSeCorreEmNenhumaVaza() {
        // Beto vence a 1ª vaza e abre a 2ª.
        MesaDeTeste mesa = mesa().jogar(BETO, "3♠").jogar(ANA, "4♣");
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).doesNotContain(new Correr());

        mesa.jogar(BETO, "5♦");
        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).doesNotContain(new Correr());
        assertThat(MOTOR.aplicar(mesa.estado(), ANA, new Correr())).isEqualTo(new Rejeitada(ACAO_INVALIDA));
    }

    @Test
    @DisplayName("RG-AUM-3 e RG-AUM-6: diante de um pedido de aumento, quem responde pode correr")
    void correDeUmPedido() {
        MesaDeTeste mesa = mesa().pedirAumento(BETO);

        assertThat(MOTOR.acoesLegais(mesa.estado(), ANA)).contains(new Correr());

        mesa.correr(ANA);

        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 1));
    }

    @Test
    @DisplayName("Não se corre fora da própria vez")
    void correrForaDaVez() {
        assertThat(MOTOR.aplicar(mesa().estado(), ANA, new Correr())).isEqualTo(new Rejeitada(NAO_E_A_VEZ_DO_JOGADOR));
    }
}
