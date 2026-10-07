package io.github.joaomarcosvs.truco.partida;

import static io.github.joaomarcosvs.truco.carta.NotacaoDeCartas.carta;
import static io.github.joaomarcosvs.truco.partida.Partidas.ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DA_ANA;
import static io.github.joaomarcosvs.truco.partida.Partidas.EQUIPE_DO_BETO;
import static io.github.joaomarcosvs.truco.partida.Partidas.MOTOR;
import static io.github.joaomarcosvs.truco.partida.Partidas.configuracao;
import static io.github.joaomarcosvs.truco.partida.Partidas.passarDescarte;
import static io.github.joaomarcosvs.truco.partida.Partidas.primeiraAcaoLegal;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.joaomarcosvs.truco.evento.CartaJogada;
import io.github.joaomarcosvs.truco.evento.CartasDistribuidas;
import io.github.joaomarcosvs.truco.evento.PlacarAtualizado;
import io.github.joaomarcosvs.truco.evento.RodadaFinalizada;
import io.github.joaomarcosvs.truco.evento.RodadaIniciada;
import io.github.joaomarcosvs.truco.evento.VazaFinalizada;
import io.github.joaomarcosvs.truco.partida.FaseDaRodada.AguardandoJogada;
import io.github.joaomarcosvs.truco.partida.ResultadoDaVaza.Vencida;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Vazas com vira 7♦ (manilha 10). Ana dá as cartas, então Beto abre a 1ª vaza. */
class VazaTest {

    @Test
    @DisplayName("RG-VAZA-1: cada jogador joga uma carta por vez, e a vez passa para o jogador à direita")
    void umaCartaPorVez() {
        MesaDeTeste mesa = new MesaDeTeste(
                Cenario.comVira("7♦").mao(BETO, "3♠ A♥ 5♦").mao(ANA, "4♣ A♠ 6♦").montar());

        mesa.jogar(BETO, "3♠");

        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoJogada(ANA));
        assertThat(MOTOR.acoesLegais(mesa.estado(), BETO)).isEmpty();
        assertThat(mesa.estado().rodada().maoDe(BETO)).containsExactly(carta("A♥"), carta("5♦"));
        assertThat(mesa.eventos(CartaJogada.class)).containsExactly(new CartaJogada(BETO, carta("3♠"), 1));

        mesa.jogar(ANA, "4♣");

        assertThat(mesa.eventos(VazaFinalizada.class))
                .containsExactly(new VazaFinalizada(1, new Vencida(BETO, EQUIPE_DO_BETO)));
    }

    @Test
    @DisplayName("RG-VAZA-2: vence a vaza a carta mais forte, e quem venceu abre a seguinte")
    void maisForteVenceEAbreASeguinte() {
        MesaDeTeste mesa = new MesaDeTeste(
                Cenario.comVira("7♦").mao(BETO, "5♦ A♥ 4♦").mao(ANA, "3♠ 6♦ 7♠").montar());

        mesa.jogar(BETO, "5♦").jogar(ANA, "3♠");

        assertThat(mesa.eventos(VazaFinalizada.class))
                .containsExactly(new VazaFinalizada(1, new Vencida(ANA, EQUIPE_DA_ANA)));
        assertThat(mesa.estado().rodada().fase()).isEqualTo(new AguardandoJogada(ANA));
    }

    @Test
    @DisplayName("RG-VAZA-3 e RG-PARTIDA-5: quem vence duas vazas vence a rodada, sem jogar a terceira")
    void duasVazasDecidem() {
        MesaDeTeste mesa = new MesaDeTeste(Cenario.comVira("7♦")
                .mao(BETO, "10♣ 3♠ 4♦")
                .mao(ANA, "A♠ 2♥ 5♣")
                .montar());

        mesa.jogar(BETO, "10♣").jogar(ANA, "5♣").jogar(BETO, "3♠").jogar(ANA, "2♥");

        assertThat(mesa.eventos(VazaFinalizada.class))
                .extracting(VazaFinalizada::numeroDaVaza)
                .containsExactly(1, 2);
        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 1));
        assertThat(mesa.estado().placar().pontosDe(EQUIPE_DO_BETO)).isEqualTo(1);
        assertThat(mesa.estado().placar().pontosDe(EQUIPE_DA_ANA)).isZero();
        assertThat(mesa.eventos(PlacarAtualizado.class))
                .containsExactly(new PlacarAtualizado(mesa.estado().placar()));
    }

    @Test
    @DisplayName("RG-VAZA-1: com uma vaza para cada jogador, a terceira decide a rodada")
    void terceiraVazaDecide() {
        MesaDeTeste mesa = new MesaDeTeste(
                Cenario.comVira("7♦").mao(BETO, "3♠ 4♦ 2♣").mao(ANA, "2♥ 5♣ 6♦").montar());

        mesa.jogar(BETO, "3♠").jogar(ANA, "2♥"); // Beto vence a 1ª e abre a 2ª
        mesa.jogar(BETO, "4♦").jogar(ANA, "5♣"); // Ana vence a 2ª e abre a 3ª
        mesa.jogar(ANA, "6♦").jogar(BETO, "2♣"); // Beto vence a 3ª

        assertThat(mesa.eventos(VazaFinalizada.class))
                .extracting(VazaFinalizada::resultado)
                .containsExactly(
                        new Vencida(BETO, EQUIPE_DO_BETO),
                        new Vencida(ANA, EQUIPE_DA_ANA),
                        new Vencida(BETO, EQUIPE_DO_BETO));
        assertThat(mesa.eventos(RodadaFinalizada.class)).containsExactly(new RodadaFinalizada(1, EQUIPE_DO_BETO, 1));
    }

    @Test
    @DisplayName("RG-PARTIDA-1 e RG-PARTIDA-4: ao fim da rodada começa outra, o carteador passa para a direita"
            + " e o seguinte a ele abre a 1ª vaza")
    void proximaRodada() {
        MesaDeTeste mesa = new MesaDeTeste(Cenario.comVira("7♦")
                .mao(BETO, "10♣ 3♠ 4♦")
                .mao(ANA, "A♠ 2♥ 5♣")
                .montar());

        mesa.jogar(BETO, "10♣").jogar(ANA, "5♣").jogar(BETO, "3♠").jogar(ANA, "2♥");

        EstadoDaPartida estado = mesa.estado();
        Rodada rodada = estado.rodada();
        assertThat(estado.numeroDaRodada()).isEqualTo(2);
        assertThat(estado.carteador()).isEqualTo(BETO);
        assertThat(rodada.fase()).isInstanceOf(FaseDaRodada.AguardandoDescarte.class); // RG-DESC-2
        assertThat(passarDescarte(estado).rodada().fase()).isEqualTo(new AguardandoJogada(ANA));
        assertThat(rodada.maoDe(ANA)).hasSize(3);
        assertThat(rodada.maoDe(BETO)).hasSize(3);
        assertThat(rodada.vazas()).isEmpty();
        assertThat(mesa.eventos(RodadaIniciada.class)).containsExactly(new RodadaIniciada(2, BETO, 1, rodada.vira()));
        assertThat(mesa.eventos(CartasDistribuidas.class))
                .containsExactly(
                        new CartasDistribuidas(ANA, rodada.maoDe(ANA)),
                        new CartasDistribuidas(BETO, rodada.maoDe(BETO)));
    }

    @Test
    @DisplayName("RG-PARTIDA-4: no 1x1, os dois jogadores se alternam como carteador")
    void carteadoresSeAlternam() {
        EstadoDaPartida estado = MOTOR.novaPartida(configuracao(7));
        List<JogadorId> carteadores = new ArrayList<>(List.of(estado.carteador()));
        while (carteadores.size() < 6) {
            int rodadaAntes = estado.numeroDaRodada();
            estado = primeiraAcaoLegal(estado).novoEstado();
            if (estado.numeroDaRodada() > rodadaAntes) {
                carteadores.add(estado.carteador());
            }
        }

        for (int i = 1; i < carteadores.size(); i++) {
            assertThat(carteadores.get(i)).isNotEqualTo(carteadores.get(i - 1));
        }
    }
}
