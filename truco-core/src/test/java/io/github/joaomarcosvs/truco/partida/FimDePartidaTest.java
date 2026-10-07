package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.partida.MotivoDeRejeicao.PARTIDA_FINALIZADA;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.acao.Acao;
import io.github.joaomarcosvs.truco.acao.Aceitar;
import io.github.joaomarcosvs.truco.acao.Correr;
import io.github.joaomarcosvs.truco.acao.JogarCarta;
import io.github.joaomarcosvs.truco.acao.PedirAumento;
import io.github.joaomarcosvs.truco.evento.PartidaFinalizada;
import io.github.joaomarcosvs.truco.evento.PlacarAtualizado;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.regras.paulista.TrucoPaulista;
import io.github.joaomarcosvs.truco.visao.VisaoDoJogador;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fim da partida com vira 7♦ (manilha 10). Ana dá as cartas e Beto abre; com truco aceito, Beto vence a 1ª vaza (3♠
 * contra 4♣) e a 2ª empata (A♥ contra A♠), então Beto vence a rodada valendo 3.
 */
class FimDePartidaTest {

    private static MesaDeTeste mesa(int pontosDaAna, int pontosDoBeto) {
        return new MesaDeTeste(Cenario.comVira("7♦")
                .placar(pontosDaAna, pontosDoBeto)
                .mao(BETO, "3♠ A♥ 5♦")
                .mao(ANA, "4♣ A♠ 6♦")
                .montar());
    }

    private static MesaDeTeste betoVenceComTruco(MesaDeTeste mesa) {
        return mesa.pedirAumento(BETO)
                .aceitar(ANA)
                .jogar(BETO, "3♠")
                .jogar(ANA, "4♣")
                .jogar(BETO, "A♥")
                .jogar(ANA, "A♠");
    }

    @Test
    @DisplayName("RG-FIM-1 e RG-PARTIDA-1: X com 9 pontos vence uma rodada valendo 3, chega a 12 e vence a partida na"
            + " hora, sem nova rodada")
    void chegarA12TerminaAPartida() {
        MesaDeTeste mesa = betoVenceComTruco(mesa(4, 9));

        Placar placar = new Placar(Map.of(EQUIPE_DA_ANA, 4, EQUIPE_DO_BETO, 12));
        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 3));
        assertThat(mesa.eventos(PlacarAtualizado.class)).containsExactly(new PlacarAtualizado(placar));
        assertThat(mesa.eventos()).endsWith(new PartidaFinalizada(EQUIPE_DO_BETO, placar));
        assertThat(mesa.eventos(RodadaIniciada.class)).isEmpty();
        assertThat(mesa.estado().placar()).isEqualTo(placar);
        assertThat(mesa.estado().numeroDaRodada()).isEqualTo(1);
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new FaseDaRodada.PartidaFinalizada(EQUIPE_DO_BETO));
    }

    @Test
    @DisplayName("RG-FIM-1: a rodada final fica no estado, com a última vaza e as 40 cartas")
    void rodadaFinalFicaNoEstado() {
        Rodada rodada = betoVenceComTruco(mesa(4, 9)).estado().rodada();

        assertThat(rodada.vazas()).hasSize(2);
        assertThat(rodada.vazaAtual()).isEmpty();
        assertThat(CartasPresentes.naRodada(rodada))
                .containsExactlyInAnyOrderElementsOf(
                        new TrucoPaulista().composicaoDoBaralho().cartas());
    }

    @Test
    @DisplayName("RG-FIM-1: o vencedor pode passar de 12 pontos")
    void passarDe12() {
        MesaDeTeste mesa = betoVenceComTruco(mesa(0, 10));

        assertThat(mesa.estado().placar().pontosDe(EQUIPE_DO_BETO)).isEqualTo(13);
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new FaseDaRodada.PartidaFinalizada(EQUIPE_DO_BETO));
    }

    @Test
    @DisplayName("RG-AUM-1: quem vence uma rodada valendo 12 vence a partida, mesmo sem ter pedido o doze")
    void rodadaDeDozeVenceAPartida() {
        // Ana pede o doze e Beto aceita; Beto vence a rodada.
        MesaDeTeste mesa = mesa(0, 0)
                .pedirAumento(BETO)
                .pedirAumento(ANA)
                .pedirAumento(BETO)
                .pedirAumento(ANA)
                .aceitar(BETO)
                .jogar(BETO, "3♠")
                .jogar(ANA, "4♣")
                .jogar(BETO, "A♥")
                .jogar(ANA, "A♠");

        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 12));
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new FaseDaRodada.PartidaFinalizada(EQUIPE_DO_BETO));
    }

    @Test
    @DisplayName("RG-FIM-1: a partida também termina quando o adversário corre e entrega os pontos que faltavam")
    void terminarPorCorrida() {
        MesaDeTeste mesa = mesa(9, 4).pedirAumento(BETO).aceitar(ANA).correr(BETO);

        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DA_ANA, 3));
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new FaseDaRodada.PartidaFinalizada(EQUIPE_DA_ANA));
    }

    @Test
    @DisplayName("RG-PARTIDA-1: abaixo de 12 pontos, a partida segue com a rodada seguinte")
    void abaixoDe12Continua() {
        MesaDeTeste mesa = betoVenceComTruco(mesa(4, 6));

        assertThat(mesa.estado().placar().pontosDe(EQUIPE_DO_BETO)).isEqualTo(9);
        assertThat(mesa.eventos(PartidaFinalizada.class)).isEmpty();
        assertThat(mesa.estado().numeroDaRodada()).isEqualTo(2);
        assertThat(mesa.estado().rodada().fase()).isNotInstanceOf(FaseDaRodada.PartidaFinalizada.class);
    }

    @Test
    @DisplayName("RG-FIM-1: com a partida finalizada, ninguém tem ação legal, e qualquer ação é rejeitada")
    void depoisDoFimNadaEAceito() {
        EstadoDaPartida fim = betoVenceComTruco(mesa(4, 9)).estado();

        for (JogadorId jogador : List.of(ANA, BETO)) {
            assertThat(MOTOR.acoesLegais(fim, jogador)).isEmpty();
            for (Acao acao : List.of(new JogarCarta(0), new PedirAumento(), new Aceitar(), new Correr())) {
                assertThat(MOTOR.aplicar(fim, jogador, acao)).isEqualTo(new Rejeitada(PARTIDA_FINALIZADA));
            }
        }
    }

    @Test
    @DisplayName("RG-FIM-1: a visão mostra a vencedora da partida e não indica a vez de ninguém")
    void visaoDoFim() {
        EstadoDaPartida fim = betoVenceComTruco(mesa(4, 9)).estado();

        for (JogadorId jogador : List.of(ANA, BETO)) {
            VisaoDoJogador visao = MOTOR.visaoDe(fim, jogador);
            assertThat(visao.vencedoraDaPartida()).hasValue(EQUIPE_DO_BETO);
            assertThat(visao.vezDe()).isEmpty();
            assertThat(visao.placar().pontosDe(EQUIPE_DO_BETO)).isEqualTo(12);
        }
        assertThat(MOTOR.visaoDe(mesa(4, 9).estado(), ANA).vencedoraDaPartida()).isEqualTo(Optional.empty());
    }
}
