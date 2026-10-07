package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.ACAO_INVALIDA;
import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.JOGADOR_DESCONHECIDO;
import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.NAO_E_A_VEZ_DO_JOGADOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.JogarCarta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Ações inválidas não lançam exceção: voltam como Rejeitada, com o motivo (CLAUDE.md, princípio 5). */
class RejeicaoTest {

    private final EstadoDaPartida estado =
            Cenario.comVira("7♦").mao(BETO, "3♠ A♥ 5♦").mao(ANA, "4♣ A♠ 6♦").montar();

    @Test
    @DisplayName("Quem não está na partida é rejeitado e não tem ações legais")
    void jogadorDesconhecido() {
        JogadorId zeca = new JogadorId("zeca");

        assertThat(MOTOR.aplicar(estado, zeca, new JogarCarta(0))).isEqualTo(new Rejeitada(JOGADOR_DESCONHECIDO));
        assertThat(MOTOR.acoesLegais(estado, zeca)).isEmpty();
    }

    @Test
    @DisplayName("RG-VAZA-1: jogar fora da vez é rejeitado")
    void foraDaVez() {
        assertThat(MOTOR.aplicar(estado, ANA, new JogarCarta(0))).isEqualTo(new Rejeitada(NAO_E_A_VEZ_DO_JOGADOR));
    }

    @ParameterizedTest(name = "posição {0}")
    @ValueSource(ints = {-1, 3, 40})
    @DisplayName("Uma posição que não existe na mão é rejeitada")
    void posicaoInexistente(int indiceNaMao) {
        assertThat(MOTOR.aplicar(estado, BETO, new JogarCarta(indiceNaMao))).isEqualTo(new Rejeitada(ACAO_INVALIDA));
    }

    @Test
    @DisplayName("Depois de uma jogada, as posições acompanham a mão, que ficou menor")
    void posicoesAcompanhamAMao() {
        // Beto joga 3♠, vence a vaza e abre a seguinte com duas cartas na mão.
        MesaDeTeste mesa = new MesaDeTeste(estado).jogar(BETO, "3♠").jogar(ANA, "4♣");

        assertThat(mesa.estado().rodada().maoDe(BETO)).containsExactly(carta("A♥"), carta("5♦"));
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).containsExactly(new JogarCarta(0), new JogarCarta(1));
        assertThat(MOTOR.aplicar(mesa.estado(), BETO, new JogarCarta(2))).isEqualTo(new Rejeitada(ACAO_INVALIDA));
    }
}
