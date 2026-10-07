package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.ACAO_INVALIDA;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.JogarEncoberta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.carta.Carta;
import io.github.joaomarcosvs.truco.evento.CartaJogada;
import io.github.joaomarcosvs.truco.evento.JogadorCorreu;
import io.github.joaomarcosvs.truco.evento.PartidaFinalizada;
import io.github.joaomarcosvs.truco.evento.PlacarAtualizado;
import io.github.joaomarcosvs.truco.evento.RodadaEscurinhoIniciada;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.evento.Visibilidade;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoJogada;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Rodada Escurinho com vira 7♦ (manilha 10), com 11 a 11. Ana dá as cartas e Beto abre; Beto vence a 1ª vaza (3♠
 * contra 4♣) e a 2ª empata (A♥ contra A♠).
 */
class RodadaEscurinhoTest {

    private static MesaDeTeste mesa() {
        return new MesaDeTeste(Cenario.comVira("7♦")
                .placar(11, 11)
                .mao(BETO, "3♠ A♥ 5♦")
                .mao(ANA, "4♣ A♠ 6♦")
                .montar());
    }

    @Test
    @DisplayName("RG-ESCURINHO-1, RG-DESC-9 e RG-VIS-3: com 11 a 11, a rodada seguinte é Escurinho, sem distribuição"
            + " visível e sem descarte")
    void chegarA11A11ComecaAEscurinho() {
        // Beto tem 11 e Ana 10: na Rodada de Onze, Beto corre e Ana ganha 1.
        MesaDeTeste mesa = new MesaDeTeste(Cenario.comVira("7♦")
                        .placar(10, 11)
                        .mao(BETO, "3♠ A♥ 5♦")
                        .mao(ANA, "4♣ A♠ 6♦")
                        .montar())
                .correr(BETO);

        EstadoDaPartida estado = mesa.estado();
        Placar placar = new Placar(Map.of(EQUIPE_DA_ANA, 11, EQUIPE_DO_BETO, 11));
        assertThat(mesa.eventos())
                .containsExactly(
                        new JogadorCorreu(BETO),
                        new RodadaFinalizada(1, EQUIPE_DA_ANA, 1),
                        new PlacarAtualizado(placar),
                        new RodadaIniciada(2, BETO, 1, estado.rodada().vira()),
                        new RodadaEscurinhoIniciada());
        assertThat(estado.rodada().tipo()).isEqualTo(new TipoDeRodada.Escurinho());
        assertThat(estado.rodada().descarte().encerrado()).isTrue();
        assertThat(estado.rodada().fase()).isEqualTo(new AguardandoJogada(ANA));
        assertThat(MOTOR.acoesLegais(estado, ANA))
                .containsExactly(new JogarCarta(0), new JogarCarta(1), new JogarCarta(2), new Correr());
    }

    @Test
    @DisplayName("RG-VIS-3: na Escurinho, a visão não mostra a própria mão, só quantas cartas cada um tem")
    void visaoSemAPropriaMao() {
        EstadoDaPartida estado = mesa().estado();

        for (JogadorId jogador : List.of(ANA, BETO)) {
            VisaoDoJogador visao = MOTOR.visaoDe(estado, jogador);
            assertThat(visao.tipoDaRodada()).isEqualTo(new TipoDeRodada.Escurinho());
            assertThat(visao.mao()).isEmpty();
            assertThat(visao.cartasNaMao()).isEqualTo(Map.of(ANA, 3, BETO, 3));
            for (Carta carta : estado.rodada().maoDe(jogador)) {
                assertThat(CartasPresentes.mostra(visao, carta))
                        .as("%s vê %s", jogador, carta)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("RG-ESCURINHO-1: na Escurinho não há aumento nem carta encoberta, em nenhuma vaza")
    void semAumentoNemEncoberta() {
        MesaDeTeste mesa = mesa();

        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO))
                .containsExactly(new JogarCarta(0), new JogarCarta(1), new JogarCarta(2), new Correr());
        assertThat(MOTOR.aplicar(mesa.estado(), BETO, new PedirAumento())).isEqualTo(new Rejeitada(ACAO_INVALIDA));

        mesa.jogar(BETO, "3♠").jogar(ANA, "4♣");

        // Na 2ª vaza, a carta encoberta seria permitida numa rodada normal (RG-ENC-1).
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO))
                .containsExactly(new JogarCarta(0), new JogarCarta(1), new Correr());
        assertThat(MOTOR.aplicar(mesa.estado(), BETO, new JogarEncoberta(0))).isEqualTo(new Rejeitada(ACAO_INVALIDA));
    }

    @Test
    @DisplayName("RG-ESCURINHO-1: a rodada vale 1, e quem a vence vence a partida")
    void quemVenceARodadaVenceAPartida() {
        MesaDeTeste mesa = mesa();

        assertThat(mesa.estado().rodada().aposta().valor()).isEqualTo(1);

        mesa.jogar(BETO, "3♠").jogar(ANA, "4♣").jogar(BETO, "A♥").jogar(ANA, "A♠");

        Placar placar = new Placar(Map.of(EQUIPE_DA_ANA, 11, EQUIPE_DO_BETO, 12));
        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 1));
        assertThat(mesa.eventos()).endsWith(new PartidaFinalizada(EQUIPE_DO_BETO, placar));
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new FaseDaRodada.PartidaFinalizada(EQUIPE_DO_BETO));
    }

    @Test
    @DisplayName("RG-ESCURINHO-1 e RG-AUM-6: quem corre na Escurinho entrega a rodada e, com ela, a partida")
    void correrNaEscurinho() {
        MesaDeTeste mesa = mesa().correr(BETO);

        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DA_ANA, 1));
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new FaseDaRodada.PartidaFinalizada(EQUIPE_DA_ANA));
    }

    @Test
    @DisplayName("RG-ESCURINHO-1 e RG-VIS-1: a carta jogada às cegas é revelada a todos, inclusive a quem a jogou")
    void cartaJogadaERevelada() {
        // Beto escolhe pela posição, sem saber que a primeira carta é o 3♠.
        MesaDeTeste mesa = mesa().agir(BETO, new JogarCarta(0));

        assertThat(mesa.eventos()).containsExactly(new CartaJogada(BETO, carta("3♠"), 1));
        assertThat(mesa.eventos().getFirst().visibilidade()).isEqualTo(Visibilidade.PUBLICO);
        for (JogadorId jogador : List.of(ANA, BETO)) {
            assertThat(MOTOR.visaoDe(mesa.estado(), jogador)
                            .vazaAtual()
                            .getFirst()
                            .carta())
                    .hasValue(carta("3♠"));
        }
    }
}
